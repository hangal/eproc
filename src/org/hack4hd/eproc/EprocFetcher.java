package org.hack4hd.eproc;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openqa.selenium.By;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by hangal on 12/11/16.
 */
public class EprocFetcher {
    private static Log log = LogFactory.getLog(Tender.class);

    StepDefs browser;

    private static Options getOpt()
    {
        // create the Options
        // consider a local vs. global (hosted) switch. some settings will be disabled if its in global mode
        Options options = new Options();


        //	options.addOption( "ns", "no-shutdown", false, "no auto shutdown");
        return options;
    }

    public void setupDropDownsHDMC() throws InterruptedException {
        // use xpath syntax: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/739
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:departmentId')]", "Directorate of  Municipal Administration");
        browser.dropDownSelectionByXpath("//select[input(@id, 'eprocTenders:tenderCreateDateFrom')]", "01/01/2016");
        browser.dropDownSelectionByXpath("//select[input(@id, 'eprocTenders:tenderCreateDateTo')]", "31/12/2016");
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:departmentLoc')]", "Commissioner, Hubli-Dharwad City Corporation (100913.9.1.1.13.6)");
    }

    public void setupDropDownsBRTS() throws InterruptedException {
        // use xpath syntax: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/739
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:departmentId')]", "Hubli-Dharwad BRTS Company Ltd");
      //  browser.dropDownSelectionByXpath("//input[contains(@id, 'eprocTenders:tenderCreateDateFrom')]", "01/01/2011");
      //  browser.enterValueInInputField("eprocTenders:tenderCreateDateFrom", "01/01/2011");
      //  browser.enterValueInInputField("eprocTenders:tenderCreateDateTo", "31/12/2016");

    //    browser.dropDownSelectionByXpath("//input[contains(@id, 'eprocTenders:tenderCreateDateTo')]", "31/12/2016");
        browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:status')]", "Published"); // options: Closed, Under Evaluation, Evaluation Completed, Awarded, Evaluation suspended, No Bids Received, Recalled, Retendered, Finalized
    }

    // walks through all the pages
    private List<Tender> getAllTenders(String dir, String tenderStatus) throws IOException, InterruptedException {
        List<Tender> allTenders = new ArrayList<>();

        int nextPage = 2;
        while (true) {
            // get all tenders on current page
            List<Tender> tendersOnPage = browser.getTendersOnPage(dir, tenderStatus);
            allTenders.addAll(tendersOnPage);

            // try and click through to the next page
            String xpath = "//table[@class = 'scroller']//a[text() = '" + nextPage + "']";
            try {
                browser.clickOnXpath(xpath);
            } catch (Exception e) {
                log.warn("OK, no next page #" + nextPage);
                break;
            }
            nextPage++;
        }

        return allTenders;
    }

    public void doIt(String[] args) throws ParseException, InterruptedException, IOException {
        Options options = getOpt();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);
        browser = new StepDefs();
        browser.openBrowser();
        browser.openURL("https://eproc.karnataka.gov.in/eprocurement/common/eproc_tenders_list.seam");
        browser.waitFor(2);

//        setupDropDownsHDMC();
        setupDropDownsBRTS();

        String[] tenderStatuses = new String[]{"Evaluation Completed"}; // "Published", "Under Evaluation", , "Awarded"};

        List<Tender> allTenders = new ArrayList<>();

        String dir = "brts-data";

        try {
            for (String tenderStatus : tenderStatuses) {
                // link for view notice inviting tender details
                browser.dropDownSelectionByXpath("//select[contains(@id, 'eprocTenders:status')]", tenderStatus);
                browser.clickOnXpath("//input[contains(@id, 'eprocTenders:butSearch')]");
                allTenders.addAll (getAllTenders("brts-data", tenderStatus));
            }
        } catch (Exception e) {
            browser.updateTestStatus("Sorry, download failed! " + e);
        }

        String prefix = "all-tenders-" + Tender.sdf.format(new Date());
        Util.writeObjectToFile (dir + File.separator + prefix + ".ser", (java.io.Serializable) allTenders);

        // write out the CSV file for all tenders with these fields
        if (allTenders.size() > 0) {
            String NEW_LINE_SEPARATOR = "\n";
            CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
            Writer fileWriter = new FileWriter(dir + File.separator + prefix + ".csv");

            CSVPrinter csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
            List<String> colNamesList = Util.getInstanceFieldNames(allTenders.get(0));
            csvFilePrinter.print(colNamesList);

            for (Tender tender: allTenders) {
                List<String> colList = Util.getInstanceFields(tender);
                csvFilePrinter.print(colList);
            }

            fileWriter.close();
        }
    }

    public static void main (String args[]) throws ParseException, InterruptedException, IOException {
        new EprocFetcher().doIt(args);
    }
}


