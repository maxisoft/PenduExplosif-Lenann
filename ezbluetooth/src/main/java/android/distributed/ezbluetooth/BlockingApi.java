package android.distributed.ezbluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.util.SparseArray;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Api bloquante.
 * <br/> Cette Api permet d'utiliser les primitives d'envoi et de réception de maniere bloquante.
 */
public class BlockingApi implements Closeable {
    /**
     * L'interface du service Android.
     */
    private final EZBluetoothService.Binder binder;

    /**
     * Le {@link Context} à utiliser pour la liaison avec le service
     */
    private final Context context;

    /**
     * Un lock utilisé pour créer des {@link Condition}s
     */
    private final ReentrantLock lock;

    /**
     * Liste des conditions pour les envois bloquants.
     */
    private final SparseArray<Condition> sendConditions;

    /**
     * Un {@link BroadcastReceiver} pour la gestions des communications entrantes avec le service
     */
    private final BroadcastReceiver receiver;

    /**
     * La file qui permet de stocker temporairement les réceptions
     */
    private final BlockingQueue<RecvMessage> recvQueue;

    /**
     * @param context Le {@link Context} à utiliser pour la liaison avec le service
     * @param binder  L'interface du service Android.
     */
    public BlockingApi(Context context, EZBluetoothService.Binder binder) {
        this(context, binder, 50);
    }

    /**
     * @param context       Le {@link Context} à utiliser pour la liaison avec le service
     * @param binder        L'interface du service Android.
     * @param recvQueueSize le nombre maximum de message à stocker temporairement
     */
    public BlockingApi(Context context, EZBluetoothService.Binder binder, int recvQueueSize) {
        this.binder = binder;
        this.context = context;
        lock = new ReentrantLock();
        sendConditions = new SparseArray<Condition>();
        receiver = new BlockingApiBroadcastReceiver();
        recvQueue = new ArrayBlockingQueue<>(recvQueueSize, true);
        IntentFilter filter = new IntentFilter();
        filter.addAction(EZBluetoothService.ACTION_RECV);
        filter.addAction(EZBluetoothService.ACTION_ACK_RECV);
        context.registerReceiver(receiver, filter);
    }

    /**
     * Envoi bloquant.
     * <br/> Timeout de 15 secondes
     *
     * @param address l'addresse mac de destination
     * @param data    les données à envoyer
     * @return {@code true} si l'envoi s'est bien passé. {@code false} sinon
     * @throws InterruptedException
     */
    public boolean send(String address, Serializable data) throws InterruptedException {
        return send(address, data, 15, TimeUnit.SECONDS);
    }

    /**
     * Envoi bloquant.
     * <br/> Timeout de 15 secondes
     *
     * @param address l'addresse mac de destination
     * @param data    les données à envoyer
     * @param timeout le temps maximum a attendre
     * @param unit    l'unité de temps
     * @return {@code true} si l'envoi s'est bien passé. {@code false} sinon
     * @throws InterruptedException
     */
    public boolean send(String address, Serializable data, long timeout, TimeUnit unit) throws InterruptedException {
        lock.lock();
        try {
            short id = binder.send(address, data);
            return await(id, timeout, unit);
        } finally {
            lock.unlock();
        }
    }

    private boolean await(short id, long timeout, TimeUnit timeUnit) throws InterruptedException {
        lock.lock();
        try {
            Condition condition = sendConditions.get(id, null);
            if (condition == null) {
                condition = lock.newCondition();
                sendConditions.put(id, condition);
            }
            return condition.await(timeout, timeUnit);
        } finally {
            sendConditions.remove(id);
            lock.unlock();
        }
    }

    private boolean signal(short id) {
        lock.lock();
        try {
            Condition condition = sendConditions.get(id, null);
            if (condition != null) {
                condition.signal();
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    /**
     * Reception Bloquante.
     * <br/>Attention cette methode effectue une reception sur l'ensemble des sockets ouverts.
     *
     * @param timeout le temps maximum a attendre
     * @param unit    l'unité de temps
     * @return le message "wrappé" dans un {@link android.distributed.ezbluetooth.BlockingApi.RecvMessage}
     * @throws InterruptedException
     */
    public RecvMessage recv(long timeout, TimeUnit unit) throws InterruptedException {
        return recvQueue.poll(timeout, unit);
    }

    /**
     * Reception Bloquante.
     * <br/>Attention cette methode effectue une reception sur l'ensemble des sockets ouverts.
     * @return le message encapsulé dans un {@link android.distributed.ezbluetooth.BlockingApi.RecvMessage}
     */
    public RecvMessage recv() throws InterruptedException {
        return recv(15, TimeUnit.SECONDS);
    }

    /**
     * Liberation des ressources.
     */
    @Override
    public void close() {
        context.unregisterReceiver(receiver);
    }

    /**
     * Classe pour encapsuler les messages recus.
     */
    public static class RecvMessage {
        /**
         * La donnée reçu
         */
        private final Serializable data;

        /**
         * La source du message
         */
        private final String source;

        /**
         * La date de reception
         */
        private final Date date;

        RecvMessage(Serializable data, String source) {
            this.date = new Date();
            this.data = data;
            this.source = source;
        }

        /**
         *
         * @return La donnée reçu
         */
        public Serializable getData() {
            return data;
        }

        /**
         *
         * @return La source du message
         */
        public String getSource() {
            return source;
        }

        /**
         *
         * @return La date de reception
         */
        public Date getDate() {
            return date;
        }
    }

    private class BlockingApiBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String address;
            switch (intent.getAction()) {
                case EZBluetoothService.ACTION_RECV:
                    address = intent.getStringExtra(EZBluetoothService.EXTRA_RECV_SOURCE);
                    Serializable data = intent.getSerializableExtra(EZBluetoothService.EXTRA_RECV_MSG);
                    try {
                        recvQueue.add(new RecvMessage(data, address));
                    } catch (IllegalStateException e) {
                        Log.e(BlockingApi.class.getSimpleName(), "recv Queue is full", e);
                    }
                    break;
                case EZBluetoothService.ACTION_ACK_RECV:
                    short seq = intent.getShortExtra(EZBluetoothService.EXTRA_ACK_RECV_SEQ, (short) -1);
                    if (seq != -1) {
                        try {
                            signal(seq);
                        } catch (Exception e) {
                            Log.e(BlockingApi.class.getSimpleName(), "unexpeted exception using signal.", e);
                        }
                    }
                    break;
            }
        }
    }
}

