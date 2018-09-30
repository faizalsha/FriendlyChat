package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGHT_LIMIT = 1000;
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;            
    private ImageButton mPhotoPickerButton;
    private EditText mMessagEditText;
    private Button mSendButton;

    private String mUsername;

    private FirebaseDatabase mFirebaseDatabase;   //entry point to the db
    private DatabaseReference mMessagesDatabaseReference;   //reference to specific part of db
    private ChildEventListener mChildEventListener;  //attached to the @mMessagesDatebaseReference
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;

    private FirebaseAuth mFirebaseAuth;    //for firebase authentication
    private FirebaseAuth.AuthStateListener mAuthStateListener;  //triggered when auth states(logged in/out) changes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mUsername = ANONYMOUS;

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");



        //Initialize regerences to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessagEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        //initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessage = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this,R.layout.item_message,friendlyMessage);
        mMessageListView.setAdapter(mMessageAdapter);

        //initialize progress Bar
        mProgressBar.setVisibility(View.INVISIBLE);

        //select a photo from storage and upload
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                //choosing what type of data(IMAGE)will be shown in picker and in what format(JPEG)--case sensitive
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
                startActivityForResult(Intent.createChooser(intent,"Complete action using"),RC_PHOTO_PICKER);
            }
        });

        //adding a textChangeListener to the edit text
        //so that when there is nothing to send in editText the send button remain inactive
        mMessagEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //dont know the benefit of this method as of now
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0)
                    mSendButton.setEnabled(true);
                else
                    mSendButton.setEnabled(false);
            }

            @Override
            public void afterTextChanged(Editable s) {
                //dont know the benefit/use of this method as of now
            }
        });
        //limit the maximum character that can be entered in the editText/Editable
        mMessagEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGHT_LIMIT)});

        //ClickListener on Send Button
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessagEditText.getText().toString(),mUsername,null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                //clear the editText
                mMessagEditText.setText("");
            }
        });



        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser != null){
                    //logged in state
                    onSignedInInitialize(firebaseUser.getDisplayName());
                    Toast.makeText(MainActivity.this, "You are logged in now",Toast.LENGTH_SHORT).show();
                } else{
                    //logged out state
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);


                }
            }
        };
        //mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    //just to remove the exiting app from the login activity by pressing back button
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN && resultCode == RESULT_OK) {
                Toast.makeText(this, "signed in!", Toast.LENGTH_SHORT).show();
            }
            if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
                Uri selectedImageUri = data.getData();
                final StorageReference photoRef =
                        mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());
                photoRef.putFile(selectedImageUri).addOnSuccessListener
                        (this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        String downloadUrl = taskSnapshot.getUploadSessionUri().toString();
                        FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUrl);
                        mMessagesDatabaseReference.push().setValue(friendlyMessage);
                    }
                });
            }
        }


    public void onSignedInInitialize(String displayName) {
        mUsername = displayName;
        attachDataReadListener();
    }

    public void onSignedOutClean(){
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachReadListener();
    }

    private void detachReadListener() {
        if (mChildEventListener != null)
        mMessagesDatabaseReference.removeEventListener(mChildEventListener);
        mChildEventListener = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.sign_out:
                AuthUI.getInstance().signOut(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mFirebaseAuth != null)
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        detachReadListener();
        mMessageAdapter.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }


    //attach the listener to the mMessageDatabaseReference
    public void attachDataReadListener(){

        if (mChildEventListener == null){
            mChildEventListener = new ChildEventListener() {
                //Triggered first to get the initial state of the db and every time a new child is added.
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                mMessageAdapter.add(friendlyMessage);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }
}
