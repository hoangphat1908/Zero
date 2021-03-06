package com.jello.zero;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.os.ResultReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A placeholder fragment containing a simple view.
 */
public abstract class MainFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    protected static final String TAG = "MainActivity";
    protected FirebaseAuth auth;
    protected FirebaseAuth.AuthStateListener authListener;
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference();
    DatabaseReference alertRef = ref.child("alerts");
    protected ArrayList<Alert> alertList = new ArrayList<Alert>();
    protected ListView listView;
    ChildEventListener alertListener;
    protected AlertListViewAdapter alertListApdapter;
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected static final String ARG_SECTION_NUMBER = "section_number";
    private AddressResultReceiver mResultReceiver;

    public MainFragment() {
    }

    public abstract MainFragment newInstance(int sectionNumber);
    public abstract String getAlertReference();
    public abstract Alert retrieveAlert(DataSnapshot snapshot);
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        listView = (ListView)rootView.findViewById(R.id.alertListView);
        auth = FirebaseAuth.getInstance();
        authListener = new FirebaseAuth.AuthStateListener(){
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null){
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                }else{
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    notLoggedIn();
                }
            }
        };

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        return rootView;
    }

    @Override
    public void onStart(){
        super.onStart();
        alertRef = ref.child(getAlertReference());
        mResultReceiver = new AddressResultReceiver(null);
        mGoogleApiClient.connect();
        auth.addAuthStateListener(authListener);


        alertListApdapter = new AlertListViewAdapter(alertList, this.getContext());
        listView.setAdapter(alertListApdapter);
        alertListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey){
                Alert newAlert = retrieveAlert(dataSnapshot);

                String distance;

                if(mLastLocation != null){
                    Location alertLocation = new Location("");
                    alertLocation.setLatitude(newAlert.latitude);
                    alertLocation.setLongitude(newAlert.longitude);
                    distance = Math.round(alertLocation.distanceTo(mLastLocation)*0.000621371)+"";
                }else{
                    distance = "-1";
                }
                //newAlert.setKey(dataSnapshot.getKey());
                newAlert.setDistance(distance);
                String text = newAlert.toString();
                alertList.add(newAlert);
                Collections.sort(alertList, new Comparator<Alert>() {
                    @Override
                    public int compare(Alert o1, Alert o2) {

                        int d1 = Integer.parseInt(o1.getDistance());
                        int d2 = Integer.parseInt(o2.getDistance());
                        int distance = d1 - d2;
                        if(distance == 0){
                            return -(o1.confirmed - o2.confirmed);
                        }else return distance;
                       /* if(o1.distance.equals(o2.distance))
                            return -(o1.confirmed - o2.confirmed);
                        Log.d(TAG, "comparator");
                        return (int) (Double.parseDouble(o1.distance) - Double.parseDouble(o1.distance));*/
                    }
                });
                alertListApdapter.notifyDataSetChanged();
            }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };
        alertRef.addChildEventListener(alertListener);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position,
                                    long arg3)
            {
                Log.d(TAG, "starting intent");
                Alert value = (Alert)adapter.getItemAtPosition(position);
                Intent intent = new Intent(MainFragment.this.getActivity(), ViewAlertActivity.class);
                intent.putExtra("alert", value);
                intent.putExtra("type", getAlertReference());
                startActivity(intent);
            }
        });
    }

    public void confirmAlert(View v){
        Log.d(TAG, "confirm Alert");
        Alert confirmed = (Alert) v.getTag();
        Toast toast = Toast.makeText(this.getContext(), confirmed.name+ " is confirmed", Toast.LENGTH_LONG);
    }

    @Override
    public void onStop(){
        super.onStop();
        mGoogleApiClient.disconnect();
        if(authListener != null){
            auth.removeAuthStateListener(authListener);
        }
        alertRef.removeEventListener(alertListener);
        alertList.clear();
        alertListApdapter.notifyDataSetChanged();
    }

    public void notLoggedIn(){
        Intent intent = new Intent(this.getActivity(), CreateAccountActivity.class);
        startActivity(intent);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this.getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.getActivity(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this.getActivity(), new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 200);
            ActivityCompat.requestPermissions(this.getActivity(), new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 200);
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if(mLastLocation != null) {
            Intent intent = new Intent(this.getActivity(), GeocodeIntentService.class);
            intent.putExtra(Constants.RECEIVER, mResultReceiver);
            intent.putExtra(Constants.FETCH_TYPE_EXTRA, Constants.COORDINATE);
            intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
            getActivity().startService(intent);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler){
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, final Bundle resultData) {
            if (resultCode == Constants.SUCCESS_RESULT) {
                final Address address = resultData.getParcelable(Constants.RESULT_DATA);
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, address.getLocality() + address.getAdminArea());
                        FirebaseMessaging.getInstance().subscribeToTopic(address.getLocality().replaceAll(" ", "_")+"_"+address.getAdminArea().replaceAll(" ", "_"));
                    }
                });
            }else{
                Log.d(TAG, "Unable to find longitude latitude");
            }
        }
    }
}

