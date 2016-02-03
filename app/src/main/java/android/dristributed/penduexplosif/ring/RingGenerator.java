package android.dristributed.penduexplosif.ring;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RingGenerator {
    public static List<String> generate(Collection<String> addresses) {
        List<String> ret = new ArrayList<>(addresses);
        Collections.sort(ret);
        return ret;
    }
}
