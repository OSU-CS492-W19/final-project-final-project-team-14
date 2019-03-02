package com.example.spotifyremote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.spotifyremote.data.AlbumViewModel;
import com.example.spotifyremote.utils.SpotifyUtils;
import com.google.android.material.navigation.NavigationView;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements AlbumAdapter.OnAlbumClickListener, NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private AlbumViewModel mAlbumViewModel;
    private AlbumAdapter mAlbumAdapter;

    private Toast mToast;

    private void toast(String msg) {
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        mToast.show();
    }

    private DrawerLayout mDrawerLayout;
    private RecyclerView mAlbumsRV;
    private ProgressBar mLoadingIndicatorPB;
    private TextView mLoadingErrorTV;
    private TextView mAuthErrorTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);
        actionbar.setTitle(getString(R.string.new_releases_title));

        NavigationView navigationView = findViewById(R.id.nv_nav_drawer);
        navigationView.setNavigationItemSelectedListener(this);

        mAlbumViewModel = ViewModelProviders.of(this).get(AlbumViewModel.class);
        mAlbumAdapter = new AlbumAdapter(this);

        // grab ui
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mLoadingIndicatorPB = findViewById(R.id.pb_loading_indicator);
        mLoadingErrorTV = findViewById(R.id.tv_loading_error_message);
        mAuthErrorTV = findViewById(R.id.tv_auth_error_message);

        mAlbumsRV = findViewById(R.id.rv_albums);
        mAlbumsRV.setAdapter(mAlbumAdapter);
        mAlbumsRV.setLayoutManager(new LinearLayoutManager(this));
        mAlbumsRV.setHasFixedSize(true);

        // authenticate if we don't have an access token
        if (mAlbumViewModel.getAuthToken() == null) {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SpotifyUtils.CLIENT_ID, AuthenticationResponse.Type.TOKEN, SpotifyUtils.REDIRECT_URI);
            builder.setScopes(SpotifyUtils.SPOTIFY_PERMISSIONS);
            AuthenticationRequest request = builder.build();
            AuthenticationClient.openLoginActivity(this, SpotifyUtils.REQUEST_CODE, request);
        }
        else connected();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SpotifyUtils.REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    mAlbumViewModel.setAuthToken(response.getAccessToken());
                    Log.d(TAG, "successfully received access token: " + mAlbumViewModel.getAuthToken());
                    connected();
                    break;

                // Auth flow returned an error
                case ERROR:
                    Log.d(TAG, "failed to authenticate: " + response.getError());
                    // Handle error response
                    mAuthErrorTV.setVisibility(View.VISIBLE);
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }
        }
    }

    private void connected() {
        mLoadingIndicatorPB.setVisibility(View.VISIBLE);
        mAlbumViewModel.getNewReleases(SpotifyUtils.getNewReleasesUrl()).observe(this, new Observer<ArrayList<SpotifyUtils.SpotifyAlbum>>() {
            @Override
            public void onChanged(ArrayList<SpotifyUtils.SpotifyAlbum> albums) {
                mLoadingIndicatorPB.setVisibility(View.INVISIBLE);
                if (albums != null) {
                    mLoadingErrorTV.setVisibility(View.INVISIBLE);
                    mAuthErrorTV.setVisibility(View.INVISIBLE);
                    mAlbumsRV.setVisibility(View.VISIBLE);
                    mAlbumAdapter.updateAlbums(albums);
                } else {
                    mAlbumsRV.setVisibility(View.INVISIBLE);
                    mLoadingErrorTV.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onAlbumClick(SpotifyUtils.SpotifyAlbum album) {
        final String DEFAULT = getString(R.string.pref_device_id_default);
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_device_key), Context.MODE_PRIVATE);
        String deviceID = sharedPreferences.getString(getString(R.string.pref_device_id_key), DEFAULT);
        if (!deviceID.equals(DEFAULT)) {
            Log.d(TAG, "playing \"" + album.uri + "\" to device: " + deviceID);
            new PlayContextOnDeviceTask().execute(album.uri, deviceID, mAlbumViewModel.getAuthToken());
        }
    }

    class PlayContextOnDeviceTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String uri = params[0];
            String id = params[1];
            String token = params[2];
            String res = SpotifyUtils.playContextURIOnDevice(uri, id, token);
            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            if (s != null) {
                SpotifyUtils.SpotifyResponse response = SpotifyUtils.parseResponseJSON(s);
                if (response != null && response.error != null) {
                    if (response.error.status == 403) toast(getString(R.string.playback_error_premium_required));
                }
            } else {
                toast(getString(R.string.playback_successful));
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        mDrawerLayout.closeDrawers();
        switch (menuItem.getItemId()) {
            case R.id.nav_new_releases:
                return true;
            case R.id.nav_devices:
                Intent intent = new Intent(this, DevicesActivity.class);
                intent.putExtra(SpotifyUtils.SPOTIFY_AUTH_TOKEN_EXTRA, mAlbumViewModel.getAuthToken());
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}
