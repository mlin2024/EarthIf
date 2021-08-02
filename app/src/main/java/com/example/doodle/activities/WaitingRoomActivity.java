package com.example.doodle.activities;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.doodle.R;
import com.example.doodle.adapters.PlayerAdapter;
import com.example.doodle.models.Game;
import com.google.android.material.snackbar.Snackbar;
import com.parse.DeleteCallback;
import com.parse.ParseException;
import com.parse.ParseUser;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class WaitingRoomActivity extends AppCompatActivity {
    public static final String TAG = "WaitingRoomActivity";
    public static final long POLL_INTERVAL = TimeUnit.SECONDS.toMillis(2);
    public static final String DEFAULT_TIME_LIMIT = "60s";

    // Views in the layout
    private RelativeLayout waitingRoomRelativeLayout;
    private Toolbar toolbar;
    private TextView gameCodeWaitingRoomTextView;
    private RecyclerView playersRecyclerView;
    private TextView numPlayersTextView;
    private Spinner timeLimitSpinner;
    private TextView timeLimitTextView;
    private Button startGameButton;
    private TextView waitForHost;

    // Other necessary member variables
    private Game game;
    private ArrayList<ParseUser> players;
    private PlayerAdapter playerAdapter;
    private ArrayAdapter<CharSequence> timeLimitAdapter;
    private Handler updateHandler;
    private ProgressDialog startingProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_room);

        // Initialize the views in the layout
        waitingRoomRelativeLayout = findViewById(R.id.waitingRoomRelativeLayout);
        toolbar = findViewById(R.id.waitingRoomToolbar);
        gameCodeWaitingRoomTextView = findViewById(R.id.gameCodeWaitingRoomTextView);
        playersRecyclerView = findViewById(R.id.playersRecyclerView);
        numPlayersTextView = findViewById(R.id.numPlayersTextView);
        timeLimitSpinner = findViewById(R.id.timeLimitSpinner);
        timeLimitTextView = findViewById(R.id.timeLimitTextView);
        startGameButton = findViewById(R.id.startGameButton);
        waitForHost = findViewById(R.id.waitForHost);

        // Initialize other member variables
        // Unwrap the game that was passed in by the intent
        game = getIntent().getParcelableExtra(GameModeActivity.GAME_TAG);
        players = game.getPlayers();
        playerAdapter = new PlayerAdapter(this, players);
        updateHandler = new Handler(Looper.getMainLooper());
        startingProgressDialog = new ProgressDialog(WaitingRoomActivity.this);

        // Set up toolbar
        toolbar.setTitleTextColor(getResources().getColor(R.color.white, getTheme()));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Set up game code TextView
        gameCodeWaitingRoomTextView.setText(game.getGameCode());

        // Set up player RecyclerView
        // This allows for optimizations
        playersRecyclerView.setHasFixedSize(true);
        // Define 2 column grid layout with a new GridLayoutManager
        GridLayoutManager gridLayoutManager = new GridLayoutManager(WaitingRoomActivity.this, 2);
        playersRecyclerView.setLayoutManager(gridLayoutManager);
        // Set adapter
        playersRecyclerView.setAdapter(playerAdapter);

        // Set up num players TextView
        numPlayersTextView.setText(Integer.toString(players.size()));

        // Set up time limit spinner
        if (ParseUser.getCurrentUser().getObjectId().equals(game.getCreator().getObjectId())) {
            timeLimitSpinner.setVisibility(View.VISIBLE);

            // Create an ArrayAdapter using the string array and a default spinner layout
            timeLimitAdapter = ArrayAdapter.createFromResource(this, R.array.time_limit_array_seconds, R.layout.spinner_item);
            // Specify the layout to use when the list of choices appears
            timeLimitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            timeLimitSpinner.setAdapter(timeLimitAdapter);
            // Set it to the default value, 60s
            timeLimitSpinner.setSelection(timeLimitAdapter.getPosition(DEFAULT_TIME_LIMIT));

            timeLimitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int selected = getResources().getIntArray(R.array.time_limit_array)[position];
                    game.setTimeLimit(selected);
                    game.saveInBackground(waitingRoomRelativeLayout, getResources().getString(R.string.error_updating_game), () -> {});
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        else {
            timeLimitTextView.setVisibility(View.VISIBLE);
        }

        // Set up time limit text view
        timeLimitTextView.setText(game.getTimeLimit() + getResources().getString(R.string.seconds_unit));

        // Set up start button
        if (ParseUser.getCurrentUser().getObjectId().equals(game.getCreator().getObjectId())) {
            startGameButton.setVisibility(View.VISIBLE);
        }
        else {
            waitForHost.setVisibility(View.VISIBLE);
        }

        // Set up ProgressDialogs
        startingProgressDialog.setMessage(getResources().getString(R.string.starting_game));
        startingProgressDialog.setCancelable(false);

        startGameButton.setOnClickListener(v -> {
            game.setRound(1);
            game.saveInBackground(waitingRoomRelativeLayout, getResources().getString(R.string.error_starting_game), () -> {});
            startingProgressDialog.show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only start checking for new messages when the app becomes active in foreground
        updateHandler.postDelayed(updateGame, POLL_INTERVAL);
    }

    @Override
    protected void onPause() {
        // Stop background task from refreshing messages, to avoid unnecessary traffic & battery drain
        updateHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Stop background task from refreshing messages, to avoid unnecessary traffic & battery drain
        updateHandler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Add username next to profile icon
        menu.findItem(R.id.username).setTitle(ParseUser.getCurrentUser().getUsername());
        // Make the username text unclickable
        menu.findItem(R.id.username).setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.profileMenuItem:
                goProfileActivity();
                return true;
            case R.id.logoutMenuItem:
                logout();
                return true;
            case android.R.id.home:
                leaveGameDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        leaveGameDialog();
    }

    // Updates the player list, time limit, and round
    private Runnable updateGame = new Runnable() {
        @Override
        public void run() {
            try {
                game.fetch();
                if (game.getRound() > 0) { // Game has started
                    startingProgressDialog.dismiss();
                    goGameActivity();
                    finish();
                }
                else if (game.getRound() == -1) { // Game has been killed (the host left the game)
                    Toast.makeText(WaitingRoomActivity.this, getResources().getString(R.string.game_ended_by_host), Toast.LENGTH_LONG).show();
                    game.removePlayer(ParseUser.getCurrentUser());
                    game.saveInBackground(waitingRoomRelativeLayout, getResources().getString(R.string.error_updating_game), () -> {
                        // Once everyone has left, delete the game from the database
                        if (game.getPlayers().size() == 0) {
                            game.deleteInBackground();
                        }
                    });
                    finish();
                }
                playerAdapter.clear();
                playerAdapter.addAll(game.getPlayers());
                numPlayersTextView.setText(Integer.toString(players.size()));
                timeLimitTextView.setText(game.getTimeLimit() + getResources().getString(R.string.seconds_unit));
                updateHandler.postDelayed(this, POLL_INTERVAL);
            } catch (ParseException e) {
                Snackbar.make(waitingRoomRelativeLayout, getResources().getString(R.string.error_fetching_game_info), Snackbar.LENGTH_LONG).show();
            }
        }
    };

    // Handles when the player tries to leave the game
    private void leaveGameDialog() {
        // Create an alert to ask user if they really want to leave the game
        AlertDialog.Builder builder = new AlertDialog.Builder(WaitingRoomActivity.this);
        builder.setTitle(getResources().getString(R.string.sure_you_want_to_leave_game));

        // If the current user is the game creator, if they leave the game, the game immediately ends because
        // the game creator is needed to start the game
        if (ParseUser.getCurrentUser().getObjectId().equals(game.getCreator().getObjectId())) {
            builder.setMessage(getResources().getString(R.string.this_will_kill_the_game))
                .setPositiveButton(getResources().getString(R.string.leave_game), (dialog, which) -> {
                    game.setRound(-1);
                    game.removePlayer(ParseUser.getCurrentUser());
                    game.saveInBackground(waitingRoomRelativeLayout, getResources().getString(R.string.error_updating_game), () -> {});
                    finish();
                });
        }
        // Otherwise, just warn them normally
        else {
            builder.setMessage(getResources().getString(R.string.you_can_still_rejoin_before_it_starts))
                .setPositiveButton(getResources().getString(R.string.leave_game), (dialog, which) -> {
                    game.removePlayer(ParseUser.getCurrentUser());
                    game.saveInBackground(waitingRoomRelativeLayout, getResources().getString(R.string.error_updating_game), () -> {});
                    finish();
                });
        }
        builder.setNegativeButton(getResources().getString(R.string.never_mind), null);

        // Create and show the alert
        AlertDialog alert = builder.create();
        alert.show();
    }

    // Starts an intent to go to the login/signup activity
    private void goLoginSignupActivity() {
        Intent intent = new Intent(this, LoginSignupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    // Starts an intent to go to the profile activity
    private void goProfileActivity() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
    }

    // Starts an intent to go to the game activity
    private void goGameActivity() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameModeActivity.GAME_TAG, game);
        startActivity(intent);
    }

    // Logs out user and sends them back to login/signup page
    private void logout() {
        ProgressDialog logoutProgressDialog = new ProgressDialog(WaitingRoomActivity.this);
        logoutProgressDialog.setMessage(getResources().getString(R.string.logging_out));
        logoutProgressDialog.setCancelable(false);
        logoutProgressDialog.show();
        ParseUser.logOutInBackground(e -> {
            logoutProgressDialog.dismiss();
            if (e != null) { // Logout has failed
                Snackbar.make(waitingRoomRelativeLayout, getResources().getString(R.string.logout_failed), Snackbar.LENGTH_LONG).show();
            }
            else { // Logout has succeeded
                goLoginSignupActivity();
                finish();
            }
        });
    }
}