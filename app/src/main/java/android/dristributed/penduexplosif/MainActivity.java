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
import android.dristributed.penduexplosif.message.GameToken;
import android.dristributed.penduexplosif.message.InitToken;
import android.dristributed.penduexplosif.message.Ready;
import android.dristributed.penduexplosif.model.Game;
import android.dristributed.penduexplosif.utils.TextValidator;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private final SortedSet<String> devices = new TreeSet<>();
    private final AtomicInteger readyCount = new AtomicInteger();
    ArrayAdapter<String> itemsAdapter;
    private EZBluetoothService.Binder mService;
    private MainActivityBinding mBinding;
    private EditText editText;
    private GridView gridView;
    private Game game;

    private ServiceConnection mConnexion = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            //lorsque le service se lance, on enregistre son interface dans un champ
            mService = (EZBluetoothService.Binder) service;
            // et on lance la découverte réseau.
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

        gridView = (GridView) findViewById(R.id.gridView);
        itemsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        gridView.setAdapter(itemsAdapter);

        editText = (EditText) findViewById(R.id.editText);
        editText.setText("");
        editText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    play();
                    return true;

                }
                return false;
            }
        });
        editText.addTextChangedListener(new TextValidator(editText) {
            @Override
            public void validate(TextView textView, String text) {
                if (text == null || text.isEmpty()) {
                    textView.setError(null);
                } else if (!isValidAnswer(text.toLowerCase())) {
                    textView.setError("Mauvais format de réponse");
                } else if (answerAlreadyPresent(text.toLowerCase())) {
                    textView.setError("Réponse déjà donnée");
                } else {
                    textView.setError(null);
                }
            }
        });

        // creation du service
        Intent intent = new Intent(this, EZBluetoothService.class);
        bindService(intent, mConnexion, Context.BIND_AUTO_CREATE);

        // enregistrement d'un listener pour la gestion des périphériques et les nouveaux messages.
        RegisterListener.register(this, new
                        EZBluetoothListener() {
                            @Override
                            public void onRecv(@NonNull String address, Serializable data) {
                                Log.i("bluetooth", String.format("recv from %s : %s", address, data));
                                boolean handled =
                                        initNetworkRecvHandler(data)
                                                || gameRecvHandler(data);
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
                                if (devices.isEmpty()) {
                                    mBinding.setReady(false);
                                    checkReady();
                                    game = null;
                                    mBinding.setGame(game);
                                    refreshAnswersList();
                                }
                            }
                        }

        );
    }

    /**
     * Gestion de l'initialisation du réseau en anneau.
     * @param data
     * @return
     */
    private boolean initNetworkRecvHandler(Serializable data) {
        if (data instanceof Ready) {
            readyCount.getAndIncrement();
            checkReady();
            return true;
        } else if (data instanceof InitToken) {

            InitToken initToken = (InitToken) data;
            TreeSet<String> otherDevices = initToken.getDevices();
            if (otherDevices.size() != devices.size() + 1
                    || !otherDevices.contains(mService.getMacAddress())
                    || !otherDevices.containsAll(devices)) {
                //pas les mêmes devices.
                initToken.setValid(false);
            }

            boolean imLord = isLordOfTheRing();
            if (initToken.isValid() && imLord) {
                //token a fait un tour et est valide donc Le réseau est bon
                //on initie le jeu et on envoi un token avec un jeu
                game = new Game(RandomWord.randomWord());
                mService.send(nextDevice(), new GameToken(game));
                mBinding.setGame(game);
                refreshAnswersList();
            } else {
                InitToken token;
                //token invalide ou le noeud n'est pas le seigneur: envoi au suivant
                if (imLord && game == null) { //on reconstruit le jeton
                    TreeSet<String> allDevice = new TreeSet<String>(devices);
                    allDevice.add(mService.getMacAddress());
                    token = new InitToken(allDevice);
                    token.setValid(true);
                } else {
                    token = initToken;
                }

                mService.send(nextDevice(), token);
            }
            return true;
        }
        return false;
    }

    /**
     * Gestion de la logique du jeu
     * @param data
     * @return
     */
    private boolean gameRecvHandler(Serializable data) {
        if (data instanceof GameToken) {
            GameToken token = (GameToken) data;
            game = token.getLastGameState();
            mBinding.setGame(game);
            refreshAnswersList();
            mBinding.setLooser(game.getHearth() == 0);
            refreshAnswersList();
            if (game.getWinner() != null) {
                if (game.getWinner().equals(mService.getMacAddress())) {
                    //le jeton a fait un tour pour signaler le joueur gagnant.
                    //dans 5 sec on relance un nouveau jeu
                    startNewGameIn5Sec();

                    return true;
                } else {
                    // envoi du token au suivant pour faire suivre l'information du gagnant
                    mService.send(nextDevice(), token);
                    return true;
                }
            } else if (game.getHearth() <= 0) {
                startNewGameIn5Sec();
                return true;
            }


            mBinding.setOurTurn(true);
            if (editText.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
            //on attend la réponse de l'utilisateur (voir methode play)

            return true;
        }
        return false;
    }

    private void startNewGameIn5Sec() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mBinding.setWinner(false);
                mBinding.setLooser(false);
                Game game = new Game(RandomWord.randomWord());
                mBinding.setGame(game);
                refreshAnswersList();
                mService.send(nextDevice(), new GameToken(game));
            }
        }, 5_000);
    }

    private boolean checkReady() {
        if (isEveryBodyReady()) {
            Log.i("Init Network", "Every Body Ready");
            mService.stopServer(); //on n'accepte plus d'autres connexions
            if (isLordOfTheRing()) {
                TreeSet<String> allDevice = new TreeSet<String>(devices);
                allDevice.add(mService.getMacAddress());
                //on initie la phase de validation du réseau.
                mService.send(nextDevice(), new InitToken(allDevice));
            }
            return true;
        }
        return false;
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


    public boolean isValidAnswer(String answer) {
        return answer.length() == 1;
    }

    public boolean answerAlreadyPresent(String anwser) {
        return game.answerAlreadyPresent(anwser);
    }

    public void play() {
        //verification
        if (!mBinding.getOurTurn()) {
            return;
        }
        String answer = editText.getText().toString().toLowerCase().trim();
        if (!isValidAnswer(answer)) {
            return;
        }

        if (!game.addAnswer(answer)) {
            return;
        }

        refreshAnswersList();

        if (game.answerFound()) {
            mBinding.setWinner(true);
            mBinding.setScore(mBinding.getScore() + 1);
            game.setWinner(mService.getMacAddress());
            refreshAnswersList();
        } else {
            game.setHearth(game.getHearth() - 1);
        }

        mBinding.setLooser(game.getHearth() <= devices.size());

        mBinding.setOurTurn(false);
        editText.setText("");
        //envoi du jeu au prochain joueur
        mBinding.setGame(game);
        refreshAnswersList();
        GameToken token = new GameToken(game);
        mService.send(nextDevice(), token);
    }

    private void refreshAnswersList() {
        itemsAdapter.clear();
        for (String s : game.getAnswers()) {
            itemsAdapter.add(s);
        }
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
        if (id == R.id.action_rescan) {
            if (mService != null) {
                mService.startDiscovery();
            }
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

        public void onClickFloatingActionButton(View view) {
            play();
        }
    }
}
