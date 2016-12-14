package org.hack4hd.eproc;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Created by hangal on 12/14/16.
 * merges 2 serialized lists of tender files
 */
public class TenderMerge {
    public static void main (String args[]) throws IOException, ClassNotFoundException {
        List<Tender> tender1 = (List<Tender>) Util.readObjectFromFile(args[0]);
        List<Tender> tender2 = (List<Tender>) Util.readObjectFromFile(args[1]);
        tender1.addAll (tender2);
        Util.writeObjectToFile(args[3], (Serializable) tender1);
    }
}
