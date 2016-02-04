package android.dristributed.penduexplosif.message;


import android.dristributed.penduexplosif.model.Game;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GameToken implements Serializable {
    private final Game lastGameState;

    public GameToken(@NonNull Game game) {
        lastGameState = game;
    }


    @NonNull
    public Game getLastGameState() {
        return lastGameState;
    }

}
