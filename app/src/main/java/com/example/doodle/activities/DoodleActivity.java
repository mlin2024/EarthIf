package com.example.doodle.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.doodle.BitmapScaler;
import com.example.doodle.R;
import com.example.doodle.models.Doodle;
import com.google.android.material.snackbar.Snackbar;
import com.mukesh.DrawingView;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class DoodleActivity extends AppCompatActivity {
    public static final String TAG = "DoodleActivity";
    public static final float STROKE_WIDTH = 15;
    public static final String PARENT_DOODLE_ID = "ParentDoodleId";
    public static final String IN_GAME = "inGame";
    public static final String SET_ROOT = "setRoot";

    private RelativeLayout doodleRelativeLayout;
    private Toolbar toolbar;
    private ImageView parentImageView;
    private DrawingView doodleDrawingView;
    private Button doneButton;

    private Doodle parentDoodle;
    private Bitmap parentBitmap;
    private boolean inGame;
    private ProgressDialog findingProgressDialog;
    private ProgressDialog savingProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doodle);

        doodleRelativeLayout = findViewById(R.id.doodleRelativeLayout);
        toolbar = findViewById(R.id.doodleToolbar);
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        parentImageView = findViewById(R.id.parentImageView);
        doodleDrawingView = findViewById(R.id.doodleDrawingView);
        doneButton = findViewById(R.id.doneButton);

        findingProgressDialog = new ProgressDialog(DoodleActivity.this);
        findingProgressDialog.setMessage(getResources().getString(R.string.finding_doodle));
        savingProgressDialog = new ProgressDialog(DoodleActivity.this);
        savingProgressDialog.setMessage(getResources().getString(R.string.saving_doodle));

        // Get parent doodle from intent
        String parentDoodleId = getIntent().getStringExtra(PARENT_DOODLE_ID);
        findSingleDoodleByObjectId(parentDoodleId);

        // Get inGame from intent
        inGame = getIntent().getBooleanExtra(IN_GAME, false);

        // Prepare canvas
        doodleDrawingView.initializePen();
        doodleDrawingView.setPenSize(STROKE_WIDTH);
        doodleDrawingView.setPenColor(getResources().getColor(R.color.black));

        String filePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString();
        String fileName = "doodle" + System.currentTimeMillis();
        String fullPath = filePath + "/" + fileName + ".png";

        doneButton.setOnClickListener(v -> {
            doodleDrawingView.saveImage(filePath, fileName, Bitmap.CompressFormat.PNG, 100);
            Bitmap drawingBitmap = BitmapFactory.decodeFile(fullPath);
            saveDoodle(parentDoodle, parentBitmap, drawingBitmap);
        });
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
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void logout() {
        ProgressDialog logoutProgressDialog = new ProgressDialog(DoodleActivity.this);
        logoutProgressDialog.setMessage(getResources().getString(R.string.logging_out));
        logoutProgressDialog.show();
        ParseUser.logOutInBackground(e -> {
            logoutProgressDialog.dismiss();
            if (e != null) {
                Snackbar.make(doodleRelativeLayout, R.string.logout_failed, Snackbar.LENGTH_LONG).show();
            }
            else {
                goLoginSignupActivity();
                finish();
            }
        });
    }

    private void findSingleDoodleByObjectId(String objectId) {
        if (objectId == null) return;

        // Specify what type of data we want to query - Doodle.class
        ParseQuery<Doodle> query = ParseQuery.getQuery(Doodle.class);

        findingProgressDialog.show();
        // Start an asynchronous call for the doodle
        query.getInBackground(objectId, (foundDoodle, e) -> {
            if (e != null) { // Query has failed
                Snackbar.make(doodleRelativeLayout, R.string.error_finding_doodle, Snackbar.LENGTH_LONG).show();
                findingProgressDialog.dismiss();
                finish();
            }
            else { // Query has succeeded
                parentDoodle = foundDoodle;
                parentBitmap = getBitmapFromDoodle(parentDoodle);
                // Put the parent drawing on the canvas if it exists
                if (parentBitmap != null) parentImageView.setImageBitmap(parentBitmap);
            }
        });
    }

    private Bitmap getBitmapFromDoodle(Doodle doodle) {
        if (doodle == null) return null;
        else {
            try {
                byte[] bitmapData = doodle.getImage().getData();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);
                findingProgressDialog.dismiss();
                return bitmap;
            } catch (ParseException e) {
                Snackbar.make(doodleRelativeLayout, R.string.error_finding_doodle, Snackbar.LENGTH_LONG).show();
                findingProgressDialog.dismiss();
                return null;
            }
        }
    }

    private void saveDoodle(Doodle parentDoodle, Bitmap parentBitmap, Bitmap drawingBitmap) {
        if (parentDoodle == null) Log.e(TAG, "parentDoodle is null");

        Doodle childDoodle = new Doodle();

        // The artist is the current artist
        childDoodle.setArtist(ParseUser.getCurrentUser());
        // The image is the file that was passed in
        ParseFile drawingFile = combineBitmapsToParseFile(drawingBitmap, parentBitmap);
        childDoodle.setImage(drawingFile);
        // The parent is the doodle that was passed in via intent
        // If it has no parent, just don't set it and let it default to the default defined in the database
        if (parentDoodle != null) childDoodle.setParent(parentDoodle);
        // The tail length is just one longer than it's parent
        // If it doesn't have a parent, don't set it and it will default to 1 as defined in the database
        if (parentDoodle != null) childDoodle.setTailLength(parentDoodle.getTailLength() + 1);
        // The root is the same as its parent
        // If it has no parent, its root is equal to its objectId, which will be set after it is saved
        // For now set its root equal to setRoot so we know we have to set it
        if (parentDoodle != null) childDoodle.setRoot(parentDoodle.getRoot());
        else childDoodle.setRoot(SET_ROOT);
        // inGame is same as the parent
        // If it has no parent, inGame is passed in by intent
        if (parentDoodle != null) childDoodle.setInGame(parentDoodle.getInGame());
        else childDoodle.setInGame(inGame);

        savingProgressDialog.show();
        // Save doodle to database
        childDoodle.saveInBackground(e -> {
            savingProgressDialog.dismiss();
            if (e != null) { // Saving doodle failed
                Snackbar.make(doodleRelativeLayout, R.string.error_saving_doodle, Snackbar.LENGTH_LONG).show();
            }
            else { // Saving doodle succeeded
                // Now if it has no parent, set its root equal to its objectId
                setRootToObjectId();

                Toast.makeText(this, R.string.doodle_submitted, Toast.LENGTH_SHORT).show();
                goHomeActivity();
            }
        });
    }

    private void setRootToObjectId() {
        // Specify what type of data we want to query - Doodle.class
        ParseQuery<Doodle> query = ParseQuery.getQuery(Doodle.class);
        // Include data referred by user key
        query.whereEqualTo(Doodle.KEY_ROOT, SET_ROOT);
        // Start an asynchronous call for the doodle
        query.findInBackground((foundDoodles, e) -> {
            if (e != null) { // Query has failed
                Snackbar.make(doodleRelativeLayout, R.string.error_saving_doodle, Snackbar.LENGTH_LONG).show();
            }
            else { // Query has succeeded
                for (Doodle doodle: foundDoodles) {
                    doodle.setRoot(doodle.getObjectId());
                    doodle.saveInBackground(e1 -> {
                        if (e1 != null) {
                            Snackbar.make(doodleRelativeLayout, R.string.error_saving_doodle, Snackbar.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private ParseFile combineBitmapsToParseFile(Bitmap drawingBitmap, Bitmap parentBitmap) {
        // TODO: get this to work - currently it is just drawing the entire drawingBitmap on top of the parentBitmap, completely concealing it
        // If it has no parent, there is nothing to overlay it with
        if (parentBitmap == null) return saveBitmapToParseFile(drawingBitmap);
        Bitmap bmOverlay = Bitmap.createBitmap(drawingBitmap.getWidth(), drawingBitmap.getHeight(), drawingBitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(parentBitmap, new Matrix(), null);
        canvas.drawBitmap(drawingBitmap, 0, 0, null);
        return saveBitmapToParseFile(bmOverlay);
    }
    private ParseFile saveBitmapToParseFile(Bitmap bitmap) {
        String fileName = "doodle" + System.currentTimeMillis() + ".png";
        Bitmap resizedBitmap = BitmapScaler.scaleToFitWidth(bitmap, 1000);
        // Configure byte output stream
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Compress the image further
        resizedBitmap.compress(Bitmap.CompressFormat.PNG, 40, bytes);
        // Save to ParseFile
        ParseFile parseFile = new ParseFile(fileName, bytes.toByteArray());
        return parseFile;
    }

    // Starts an intent to go to the home activity
    private void goHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
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
}