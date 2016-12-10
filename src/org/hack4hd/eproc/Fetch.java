package org.hack4hd.eproc;

import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.MalformedURLException;

/**
 * Created by hangal on 12/11/16.
 */
public class Fetch {
    StepDefs browser;

    private static Options getOpt()
    {
        // create the Options
        // consider a local vs. global (hosted) switch. some settings will be disabled if its in global mode
        Options options = new Options();


        //	options.addOption( "ns", "no-shutdown", false, "no auto shutdown");
        return options;
    }

    public void doIt(String[] args) throws ParseException, InterruptedException, IOException {
        Options options = getOpt();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);
        browser = new StepDefs();
        browser.openBrowser();
        browser.openURL ("https://eproc.karnataka.gov.in/eprocurement/common/eproc_tenders_list.seam");
        browser.waitFor (2);
        // use xpath syntax: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/739
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:departmentId')]", "Directorate of  Municipal Administration");
        browser.waitFor (2);
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:departmentLoc')]", "Commissioner, Hubli-Dharwad City Corporation (100913.9.1.1.13.6)");
        browser.waitFor (2);
        browser.clickOnXpath ("//input[contains(@id, 'eprocTenders:butSearch')]");
        browser.waitFor (2);
        // link for view notice inviting tender details
        browser.getTendersOnPage();
    }

    public static void main (String args[]) throws ParseException, InterruptedException, IOException {
        new Fetch().doIt(args);
    }
}


