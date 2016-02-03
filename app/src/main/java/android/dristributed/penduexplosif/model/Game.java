package android.dristributed.penduexplosif.model;


import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.dristributed.penduexplosif.BR;
import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.Collections;

public class Game extends BaseObservable implements Serializable {
    private final String word;
    private ObservableArrayList<Player> players = new ObservableArrayList<>();
    private ObservableArrayList<String> answers = new ObservableArrayList<>();

    public Game(@NonNull String word) {
        this.word = word;
        notifyPropertyChanged(BR.word);
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public ObservableArrayList<Player> getPlayers() {
        return players;
    }

    public ObservableArrayList<String> getAnswers() {
        return answers;
    }

    public boolean addAnswer(String answer) {
        int binarySearch = Collections.binarySearch(answers, answer);
        if (binarySearch >= 0) {
            return false;
        }
        answers.add((-binarySearch) + 1, answer);
        return true;
    }

    @Bindable
    public String getWord() {
        return word;
    }
}
