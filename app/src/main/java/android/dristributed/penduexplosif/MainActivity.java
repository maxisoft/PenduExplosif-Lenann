package android.dristributed.penduexplosif;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.distributed.ezbluetooth.EZBluetoothService;
import android.distributed.ezbluetooth.listener.EZBluetoothListener;
import android.distributed.ezbluetooth.listener.RegisterListener;
import android.dristributed.penduexplosif.databinding.MainActivityBinding;
import android.dristributed.penduexplosif.message.InitToken;
import android.dristributed.penduexplosif.message.Ready;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.Serializable;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private final SortedSet<String> devices = new TreeSet<>();
    private final AtomicInteger readyCount = new AtomicInteger();
    private EZBluetoothService.Binder mService;
    private MainActivityBinding mBinding;
    private ServiceConnection mConnexion = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = (EZBluetoothService.Binder) service;
            mService.startDiscovery();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        mBinding.setHandlers(new Handlers());
        mBinding.setDevices(devices);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (devices.isEmpty()){
                    if (mService != null) {
                        mService.startDiscovery();
                    }
                    new Handler().postDelayed(this, 30_000);
                }
            }
        }, 30_000);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        Intent intent = new Intent(this, EZBluetoothService.class);
        bindService(intent, mConnexion, Context.BIND_AUTO_CREATE);

        RegisterListener.register(this, new EZBluetoothListener() {
            @Override
            public void onRecv(@NonNull String address, Serializable data) {
                Log.i("bluetooth", String.format("recv from %s : %s", address, data));
                if (data instanceof Ready) {
                    readyCount.getAndIncrement();
                    checkReady();
                } else if(data instanceof InitToken){

                    InitToken initToken = (InitToken) data;
                    TreeSet<String> otherDevices = initToken.getDevices();
                    if (otherDevices.size() != devices.size() + 1
                            || !otherDevices.contains(mService.getMacAddress())
                            || !otherDevices.containsAll(devices)){
                        //pas les mêmes devices.
                        initToken.setValid(false);
                    }

                    boolean imLord = isLordOfTheRing();
                    if (initToken.isValid() && imLord){
                        //token a fait un tour et est valide donc Le réseau est bon
                        //on initie le jeu et on envoi un token avec un jeu
                        //TODO
                        Log.i("c'est bon", "cool");
                    }else{
                        InitToken token;
                        //token invalide ou le noeud n'est pas le seigneur: envoi au suivant
                        if (imLord) { //on reconstruit le jeton
                            TreeSet<String> allDevice = new TreeSet<String>(devices);
                            allDevice.add(mService.getMacAddress());
                            token = new InitToken(allDevice);
                            token.setValid(true);
                        }else{
                            token = initToken;
                        }

                        mService.send(nextDevice(), token);
                    }

                }
            }

            @Override
            public void onNewPeer(@NonNull String address) {
                Log.i("bluetooth", String.format("onNewPeer: %s", address));
                synchronized (devices) {
                    devices.add(address);
                }
                mBinding.setDevices(devices);
                if (mBinding.getReady()) {
                    mService.send(address, new Ready());
                }
            }

            @Override
            public void onPeerDisconnected(@NonNull String address) {
                Log.i("bluetooth", String.format("onPeerDisconnected: %s", address));
                synchronized (devices) {
                    devices.remove(address);
                }
                mBinding.setDevices(devices);
                if (devices.isEmpty()){
                    mBinding.setReady(false);
                    checkReady();
                }

            }
        });
    }

    private void checkReady() {
        if (isEveryBodyReady()) {
            Log.i("Init Network", "Every Body Ready");
            mService.stopServer(); //on n'accepte plus d'autres connexions
            if (isLordOfTheRing()) {
                TreeSet<String> allDevice = new TreeSet<String>(devices);
                allDevice.add(mService.getMacAddress());
                //on initie la phase de validation du réseau.
                mService.send(nextDevice(), new InitToken(allDevice));
            }
        }
    }

    private boolean isLordOfTheRing() {
        return devices.first().compareTo(mService.getMacAddress()) > 0;
    }

    private boolean isEveryBodyReady() {
        return mBinding.getReady() && readyCount.get() == devices.size();
    }

    private String nextDevice() {
        SortedSet<String> afters = devices.tailSet(mService.getMacAddress());
        if (afters.isEmpty()) {
            return devices.first();
        }
        return afters.first();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public class Handlers {
        public void onClickReady(View view) {
            mBinding.setReady(true);
            for (String address : mService.listConnectedDevices()) {
                mService.send(address, new Ready());
            }
            checkReady();
        }
    }
}
