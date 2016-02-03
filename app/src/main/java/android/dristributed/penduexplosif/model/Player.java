package android.dristributed.penduexplosif.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.dristributed.penduexplosif.BR;

public class Player extends BaseObservable {
    private String name;
    private int life = 3;

    @Bindable
    public String getName() {
        return name;
    }

    @Bindable
    public int getLife() {
        return life;
    }

    public void setName(String name) {
        this.name = name;
        notifyPropertyChanged(BR.name);
    }

    public void setLife(int life) {
        this.life = life;
        notifyPropertyChanged(BR.life);
    }

    public void decrementLife() {
        setLife(getLife() - 1);
    }
}
