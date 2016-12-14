package org.hack4hd.eproc;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hangal on 12/11/16.
 */
public class EprocFetcher {
    private static Log log = LogFactory.getLog(Tender.class);

    // search form constants
    private static final String EPROC_START_PAGE = "https://eproc.karnataka.gov.in/eprocurement/common/eproc_tenders_list.seam";
    private static final String DEPARTMENT_DROPDOWN_XPATH = "//select[contains(@id, 'eprocTenders:departmentId')]";
    private static final String DEPARTMENT_LOCATION_DROPDOWN_XPATH = "//select[contains(@id, 'eprocTenders:departmentLoc')]";
    private static final String STATUS_DROPDOWN_XPATH = "//select[contains(@id, 'eprocTenders:status')]";
    private static final String TENDER_PUBLISHED_FROM_FIELD_NAME = "eprocTenders:tenderCreateDateFrom";
    private static final String TENDER_PUBLISHED_TO_FIELD_NAME = "eprocTenders:tenderCreateDateTo";
    private static final String SEARCH_BUTTON_XPATH = "//input[contains(@id, 'eprocTenders:butSearch')]";
    public static final String LATEST_TENDER_TAG = "LATEST"; // name for the latest tender file in each directory, serialized
    public static final String TENDER_FILENAME_PREFIX = "tender-";

    // search results page constants
    public static final String TENDERS_TABLE_XPATH = "//table[@id='eprocTenders:browserTableEprocTenders']";
    public static final int N_COLS_IN_TENDER_ROW = 11; // no. of cols in the tender results table. the last column has links to sub pages
    public static final String PAGINATION_CONTROL_XPATH = "//table[@class = 'scroller']";

    // subpage constants
    public static final String SUBPAGE_HEADING_CSS = "td.pageHeading";

    // fetch configuration
    public static final boolean FETCH_TENDERS_ONLY_IF_STATUS_CHANGED = true, // if true, will fetch tender only if status has changed w.r.t. tender-LATEST
                                FETCH_ALL_BLOBS = false, // if true, will fetch all blobs
                                FETCH_ONLY_TEXT_BLOBS = true; // if true, only doc blobs will be fetched and converted to text

    public static final String RUN_TAG = "V1"; // new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format (new Date());

    BrowserController browser;

    public void setupDropDownsHDMC() throws InterruptedException {
        // use xpath syntax: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/739
        browser.dropDownSelectionByXpath (DEPARTMENT_DROPDOWN_XPATH, "Directorate of  Municipal Administration");
        browser.enterValueInInputField (TENDER_PUBLISHED_FROM_FIELD_NAME, "01/01/2016");
        browser.enterValueInInputField (TENDER_PUBLISHED_TO_FIELD_NAME, "31/12/2016");
        browser.dropDownSelectionByXpath (DEPARTMENT_LOCATION_DROPDOWN_XPATH, "Commissioner, Hubli-Dharwad City Corporation (100913.9.1.1.13.6)");
    }

    public void setupDropDownsBRTS() throws InterruptedException {
        // use xpath syntax: https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/739
        browser.dropDownSelectionByXpath(DEPARTMENT_DROPDOWN_XPATH, "Hubli-Dharwad BRTS Company Ltd");
      //  browser.dropDownSelectionByXpath("//input[contains(@id, 'eprocTenders:tenderCreateDateFrom')]", "01/01/2011");
      //  browser.enterValueInInputField("eprocTenders:tenderCreateDateFrom", "01/01/2011");
      //  browser.enterValueInInputField("eprocTenders:tenderCreateDateTo", "31/12/2016");

    //    browser.dropDownSelectionByXpath("//input[contains(@id, 'eprocTenders:tenderCreateDateTo')]", "31/12/2016");

    }

    // walks through all the pages
    private List<Tender> getAllTenders(String dir, String tenderStatus) throws IOException, InterruptedException {
        List<Tender> allTenders = new ArrayList<>();

        int nextPage = 2;
        while (true) {
            // get all tenders on current page
            List<Tender> tendersOnPage = browser.getTendersOnCurrentPage(dir, tenderStatus);
            allTenders.addAll(tendersOnPage);

            log.info ("\nStatus " + tenderStatus + ": Completed " + (nextPage-1) + " pages, " + allTenders.size() + " tenders\n");

            // try and click through to the next page
            // PAGINATION_CONTROL_XPATH is the navbar at the bottom... look for links inside where the link text is simply the #
            String xpath = PAGINATION_CONTROL_XPATH + "//a[text() = '" + nextPage + "']";
            try {
                browser.clickOnXpath(xpath);
            } catch (Exception e) {
                log.warn("OK, no next page #" + nextPage);
                break;
            }
            nextPage++;
            browser.waitFor (5); // throttle
        }

        return allTenders;
    }

    void saveTenders (String outputDir, String statusPrefix, List<Tender> tenders) throws IOException {
        Util.writeObjectToFile(outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + RUN_TAG + ".ser", (java.io.Serializable) tenders);
        Util.writeObjectToFile(outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + LATEST_TENDER_TAG + ".ser", (java.io.Serializable) tenders);

        // write out the CSV file for all tenders with these fields
        if (tenders.size() > 0) {
            String NEW_LINE_SEPARATOR = "\n";
            CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
            Writer fileWriter = new FileWriter(outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + RUN_TAG + ".csv");

            CSVPrinter csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
            List<String> colNamesList = Util.getInstanceFieldNames(tenders.get(0));
            csvFilePrinter.printRecord (colNamesList);

            for (Tender tender : tenders) {
                List<String> colList = Util.getInstanceFields(tender);
                csvFilePrinter.printRecord (colList);
            }

            fileWriter.close();
        }
    }

    public void doIt() throws InterruptedException, IOException {
        browser = new BrowserController();
        browser.openBrowser();
        browser.openURL (EPROC_START_PAGE);
        browser.waitFor(1);

        setupDropDownsHDMC();
        // setupDropDownsBRTS();

        String[] tenderStatuses = new String[]{"Published", "Closed", "Under Evaluation", "Evaluation Completed", "Awarded", "Evaluation suspended", "No Bids Received", "Recalled", "Retendered", "Finalized"};
        tenderStatuses = new String[]{"Published", "Closed", "Under Evaluation", "Evaluation Completed", "Awarded"};
        // options: "Published", "Closed", "Under Evaluation", "Evaluation Completed", "Awarded", "Evaluation suspended", "No Bids Received", "Recalled", "Retendered", "Finalized"
        List<Tender> allTenders = new ArrayList<>();

        String outputDir = "hdmc-data";

        try {
            for (String tenderStatus : tenderStatuses) {
                // link for view notice inviting tender details
                browser.dropDownSelectionByXpath(STATUS_DROPDOWN_XPATH, tenderStatus);
                browser.clickOnXpath (SEARCH_BUTTON_XPATH);
                List<Tender> tendersForThisStatus = getAllTenders(outputDir, tenderStatus);
                allTenders.addAll (tendersForThisStatus);
                log.info ("Saving " + tendersForThisStatus.size() + " tenders for status " + tenderStatus + "\n----\n\n");

                saveTenders (outputDir, tenderStatus + "-" + TENDER_FILENAME_PREFIX + RUN_TAG, tendersForThisStatus);
            }
        } catch (Exception e) {
            Util.print_exception("Sorry, download failed! ", e, log);
        }

        saveTenders (outputDir, "all-", allTenders);
        log.info (allTenders.size() + " tenders downloaded");
    }

    public static void main (String args[]) throws ParseException, InterruptedException, IOException {
        new EprocFetcher().doIt();
    }
}


