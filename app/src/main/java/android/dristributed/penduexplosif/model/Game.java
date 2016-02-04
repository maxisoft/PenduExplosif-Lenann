package android.dristributed.penduexplosif.model;


import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.dristributed.penduexplosif.BR;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;
import java.util.Collections;

public class Game extends BaseObservable implements Serializable {
    private final String word;
    @Nullable
    private String winner;
    private ObservableArrayList<String> answers = new ObservableArrayList<>();
    private int hearth = 10;

    public Game(@NonNull String word) {
        this.word = word.toLowerCase();
        notifyPropertyChanged(BR.word);
    }

    @Bindable
    @Nullable
    public String getWinner() {
        return winner;
    }

    @Bindable
    public int getHearth() {
        return hearth;
    }

    public void setHearth(int hearth) {
        this.hearth = hearth;
        notifyPropertyChanged(BR.hearth);
    }

    public void setWinner(@Nullable String winner) {
        this.winner = winner;
        notifyPropertyChanged(BR.winner);
    }

    public ObservableArrayList<String> getAnswers() {
        return answers;
    }

    public boolean addAnswer(String answer) {
        answer = answer.toLowerCase();
        int binarySearch = Collections.binarySearch(answers, answer);
        if (binarySearch >= 0) {
            return false;
        }
        answers.add((-binarySearch) - 1, answer);
        notifyPropertyChanged(BR.wordHint);
        return true;
    }

    public boolean answerFound() {
        for (int i = 0; i < word.length(); i++) {
            if (!answerAlreadyPresent("" + word.charAt(i))){
                return false;
            }
        }
        return true;
    }

    public boolean answerAlreadyPresent(String awnser) {
        return Collections.binarySearch(answers, awnser) >= 0;
    }

    @Bindable
    public String getWord() {
        return word;
    }

    @Bindable
    public String getWordHint() {
        String ret = "";
        for (int i = 0; i < word.length(); i++) {
            ret += " ";
            ret += answerAlreadyPresent(""+word.charAt(i)) ? word.charAt(i) : "_";
        }
        return ret.trim();
    }
}
