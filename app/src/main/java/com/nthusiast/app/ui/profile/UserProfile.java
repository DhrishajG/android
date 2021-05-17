package com.nthusiast.app.ui.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.nthusiast.app.R;
import com.nthusiast.app.ui.main.PreviewAdapter;
import com.nthusiast.app.ui.main.PreviewCard;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserProfile extends AppCompatActivity {

    private TextView username;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final int SELECT_PICTURE = 1;
    private ArrayList<PreviewCard> mTitleData;
    private FirebaseFirestore mDatabase;
    private StorageReference mStorage;
    private CircleImageView imageView;
    private PreviewAdapter mAdapter;
    private RecyclerView mRecycler;
    private Uri destinationUri;
    private TextView mNoPosts;
    private String uid;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        RelativeLayout relativeLayout = findViewById(R.id.user_feed_container);
        mDatabase = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance().getReference();
        username = findViewById(R.id.username);
        imageView = findViewById(R.id.profile_image);
        mNoPosts = findViewById(R.id.no_posts);
        uid = FirebaseAuth.getInstance().getUid();
        if (getIntent().getStringExtra("auth") != null) {
            uid = getIntent().getStringExtra("auth");
            imageView.setClickable(false);
        }

//        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        Objects.requireNonNull(getSupportActionBar()).hide();

        AnimationDrawable animationDrawable = (AnimationDrawable) relativeLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();

        mRecycler = findViewById(R.id.recyclerView);
        mRecycler.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        mRecycler.setClipToOutline(true);
        mRecycler.setLayoutManager(new LinearLayoutManager(this));

        mTitleData = new ArrayList<>();
        mAdapter = new PreviewAdapter(this, mTitleData, "post_expansion", 2);
        mAdapter.sendContext(imageView, username, findViewById(R.id.user_profile_holder));
        mRecycler.setAdapter(mAdapter);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(this::initializeData);
        initializeData();
    }

    @SuppressLint("SetTextI18n")
    private void initializeData() {
        mDatabase.collection("users").document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        assert document != null;
                        username.setText(Objects.requireNonNull(document.get("firstName")).toString() + " " + Objects.requireNonNull(document.get("lastName")).toString() + "'s Profile");
                        String image = "" + document.get("image");
                        if (image.length() > 0)
                            try {
                                Glide.with(UserProfile.this)
                                        .load(image)
                                        .fitCenter()
                                        .into(imageView);
                            } catch (IllegalArgumentException ignored) {} // Started load when activity destroyed

                        imageView.setContentDescription(image);
                        mTitleData.clear();
                        if (uid.equals(FirebaseAuth.getInstance().getUid()))
                            mTitleData.add(new PreviewCard(Uri.parse(image)));
                        mAdapter.notifyDataSetChanged();
                        loadPosts();
                    }
                });
    }

    private void loadPosts() {
        mDatabase.collection("users").document(uid).collection("authoredPosts")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                        for (QueryDocumentSnapshot document : Objects.requireNonNull(task.getResult())) {
                            mDatabase.collection("dorms").document((String) Objects.requireNonNull(document.get("dormName"))).collection("posts").document((String) Objects.requireNonNull(document.get("postId")))
                                    .get()
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            DocumentSnapshot document1 = task1.getResult();
                                            assert document1 != null;
                                            if (document1.get("image") != null)
                                                mTitleData.add(new PreviewCard((String) document1.get("title"), (String) document1.get("postText"), (String) document1.get("author"), document1.getId(), (String) document.get("dormName"), Uri.parse((String) document1.get("image"))));
                                            else
                                                mTitleData.add(new PreviewCard((String) document1.get("title"), (String) document1.get("postText"), (String) document1.get("author"), document1.getId(), (String) document.get("dormName")));
                                        }
                                        mAdapter.notifyDataSetChanged();
                                    });
                        }
                    swipeRefreshLayout.setRefreshing(false);
                    if (mAdapter.getItemCount() > 1)
                        runLayoutAnimation(mRecycler);
                    else mNoPosts.animate().alpha(1.0f);
                });
    }

    @Override
    public void onBackPressed() {
        supportFinishAfterTransition();
    }

    private void runLayoutAnimation(final RecyclerView recyclerView) {
        final Context context = recyclerView.getContext();
        final LayoutAnimationController controller =
                AnimationUtils.loadLayoutAnimation(context, R.anim.layout_topic_cards_animation);

        recyclerView.setLayoutAnimation(controller);
        Objects.requireNonNull(recyclerView.getAdapter()).notifyDataSetChanged();
        recyclerView.scheduleLayoutAnimation();
    }

    @SuppressLint("IntentReset")
    public void newProfileImage(View view) {
        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.setType("image/*");
        Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});
        startActivityForResult(chooserIntent, SELECT_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri localUri;
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            localUri = UCrop.getOutput(data);
            Glide.with(this)
                    .load(localUri)
                    .into(imageView);
            new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                    .setTitle("Update Profile")
                    .setMessage("Change your profile picture??")
                    .setPositiveButton("Replace", (dialogInterface, i) -> uploadImage())
                    .setNeutralButton("Retain", (dialogInterface, i) -> retainProfilePicture())
                    .show();
        } else if (resultCode == RESULT_OK && data.getData() != null) {
            localUri = data.getData();
            File file = null;
            try {
                file = File.createTempFile("temp", ".jpg");
            } catch (IOException e) {
                e.printStackTrace();
            }
            destinationUri = Uri.fromFile(file);
            UCrop.of(localUri, destinationUri)
                    .start(this);
        }
    }

    @SuppressLint("InflateParams")
    private void retainProfilePicture() {
        Glide.with(this)
                .load(imageView.getContentDescription().toString())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(imageView);
//        mTitleData.clear();
//        mAdapter.notifyDataSetChanged();
    }

    private void uploadImage() {
        final StorageReference ref = mStorage.child("profiles/" + UUID.randomUUID().toString() + ".jpg");
        UploadTask uploadTask = ref.putFile(destinationUri);
        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw Objects.requireNonNull(task.getException());
            }
            return ref.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful())
                mDatabase.collection("users").document(uid)
                        .update("image", Objects.requireNonNull(task.getResult()).toString());
        });
    }
}
