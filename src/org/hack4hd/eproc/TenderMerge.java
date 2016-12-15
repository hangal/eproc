package org.hack4hd.eproc;

import java.io.IOException;
import java.util.List;

/**
 * Created by hangal on 12/14/16.
 * merges 2 serialized lists of tender files
 */
public class TenderMerge {
    public static void main (String args[]) throws IOException, ClassNotFoundException {
        List<Tender> tenders1 = (List<Tender>) Util.readObjectFromFile(args[0]);
        List<Tender> tenders2 = (List<Tender>) Util.readObjectFromFile(args[1]);
        tenders1.addAll (tenders2);
        Tender.saveTenders (".", args[3], tenders1);
    }
}
