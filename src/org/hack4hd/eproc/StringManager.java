package org.hack4hd.eproc;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

/** Creates a disambiguated name by appending suffixes, _1, _2, etc. (modulo case) */

public class StringManager {
    private Multiset<String> lowerCaseEffectiveStringsSeen = LinkedHashMultiset.create(); // this will track all the names of subpages seen

    // use the title attribute of the img element as the subpage title.
    // however, massage it to avoid conflicts, get rid of "/" embedded in it, etc.
    // eventually, this block computes the effectiveSubPageTitle

    /* should never return the same string twice, for any sequence of inputs!
    * also replaces "/" in string with "_"
    * */
    public synchronized String registerAndDisambiguate(String str)
    {
        if (Util.nullOrEmpty(str))
            str = "NO_NAME";

        str = str.replaceAll("/", "_"); // no subdir structure should get created due to "/" which sometimes is in the title, e.g. "Addendum/s"
        String effectiveStr = str;

        String lowerCaseEffectiveStr = effectiveStr.toLowerCase();
        while (lowerCaseEffectiveStringsSeen.contains (lowerCaseEffectiveStr)) {
            lowerCaseEffectiveStr += "_";
            effectiveStr += "_";
        }

        // need to take care of the situation where a and a_1 are both original strings!
        // so the sequence a, a, a_1 will get mapped to a, a_1, a_1_1 respectively
        lowerCaseEffectiveStringsSeen.add (lowerCaseEffectiveStr);
        return effectiveStr;
    }

    public static void main (String args[]) {
        StringManager sm = new StringManager();
        if (!"a".equals(sm.registerAndDisambiguate("a")))
            throw new RuntimeException();
        if (!"A_".equals(sm.registerAndDisambiguate("A")))
            throw new RuntimeException();
        if (!"a__".equals(sm.registerAndDisambiguate("a_")))
            throw new RuntimeException();

        sm = new StringManager();
        if (!"A_".equals(sm.registerAndDisambiguate("A_")))
            throw new RuntimeException();
        if (!"a".equals(sm.registerAndDisambiguate("a")))
            throw new RuntimeException();
        if (!"a__".equals(sm.registerAndDisambiguate("a")))
            throw new RuntimeException();

        if ("View Selected Supplier_s for".equals(sm.registerAndDisambiguate("View Selected Supplier/s for "))) {
            throw new RuntimeException();
        }

        if ("View Selected Supplier_s for_".equals(sm.registerAndDisambiguate("View Selected Supplier/s for "))) {
            throw new RuntimeException();
        }

        System.out.println ("all tests passed");
    }
}

