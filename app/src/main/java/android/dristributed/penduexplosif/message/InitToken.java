package android.dristributed.penduexplosif.message;

import java.io.Serializable;
import java.util.TreeSet;


public class InitToken implements Serializable {
    private final TreeSet<String> devices;
    private boolean valid = true;


    public InitToken(TreeSet<String> devices) {
        this.devices = devices;
    }

    public TreeSet<String> getDevices() {
        return devices;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
