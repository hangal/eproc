package org.hack4hd.eproc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Tender implements Serializable {
    private static Log log = LogFactory.getLog(Tender.class);
    public static final String TENDER_FILENAME_PREFIX = "tender-";
    public static final String LATEST_TAG = "LATEST"; // name for the latest tender file in each directory, serialized

    String currentStatus;
    boolean hasTenderDetails, hasBidEvaluationResults;

    String departmentOrLocationCode, number, title, type, category, subCategory, estimatedValue, NITPublishedDate, lastDateForBidSubmission;
    String typeOfQuotation, evaluationType, department, bidValidityPeriod, noOfCalls, denominationType;

    String financialBidType, selectedSupplier, selectedCompanyName, bidAmountInFigures, bidAmountInWords;
    String allBlobNames, allSubPageNames;  // append the sub-page/blob names into a single string so it can be stored in the csv

    int numBlobs, numSubPages;

    List<String> subPages = new ArrayList<>();
    List<String> blobFiles = new ArrayList<>();
    List<String> blobLinks = new ArrayList<>();

    Map<String, String> otherFields = new LinkedHashMap<>(); // for future use

    public String getId() {
        return number;
    }

    public void addBlob(String f, String link) {
        blobFiles.add (f);
        numBlobs = blobFiles.size();
        allBlobNames = String.join (",\n", blobFiles); // deliberately use both , and \n as separator
        blobLinks.add (link);
    }

    public void addSubPage(String f) {
        subPages.add (f);
        numSubPages = subPages.size();
        allSubPageNames = String.join (",\n", subPages);  // deliberately use both , and \n as separator
    }

    public int hashCode () {
        String s = getId();
        return (s == null) ? 0 : s.hashCode();
    }

    public boolean equals (Object o) {
        String s = getId();
        if (s == null || (!(o instanceof Tender)))
            return false;
        return s.equals(((Tender) o).getId());
    }

    public static void writeToXLS (Tender compare) throws IOException {

    }

    /** saves tender as .ser and .csv in the given dir */
    public void save (String baseDir) throws IOException {
        // save in 2 places
        saveWithTag (baseDir, EprocFetcher.RUN_TAG);
        saveWithTag (baseDir, LATEST_TAG);
    }

    /** saves tender as .ser and .csv. as dir/tender-tag.ser and dir/tender-tag.csv */
    public void saveWithTag (String dir, String tag) throws IOException {
        List<String> colList = Util.getInstanceFields(this);
        List<String> colNamesList = Util.getInstanceFieldNames(this);

        // just for reference, keep the col names list. not strictly needed
        colNamesList.add ("Column names");
        colList.add (String.join (" | ", colNamesList));

        log.info ("Writing out columns for tender " + number);

        // write out .ser version
        Util.writeObjectToFile (dir + File.separator + TENDER_FILENAME_PREFIX + tag + ".ser", this);

        // write out the CSV file with these fields
        String NEW_LINE_SEPARATOR = "\n";
        CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
        Writer fileWriter = new FileWriter(dir + File.separator + TENDER_FILENAME_PREFIX + tag + ".csv");
        CSVPrinter csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
        csvFilePrinter.printRecord(colList);
        fileWriter.close();
    }

    /** prefix = dir/tag, only .csv is appended to it */
    static void writeTendersToCSV (String prefix, List<Tender> tenders) throws IOException {
        // write out the CSV file for all tenders with these fields
        if (tenders.size() > 0) {
            String NEW_LINE_SEPARATOR = "\n";
            CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
            Writer fileWriter = new FileWriter(prefix + ".csv");

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

    static void saveTenders (String outputDir, String statusPrefix, List<Tender> tenders) throws IOException {
        Util.writeObjectToFile(outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + "-" + EprocFetcher.RUN_TAG + ".ser", (java.io.Serializable) tenders);
        Util.writeObjectToFile(outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + "-" + LATEST_TAG + ".ser", (java.io.Serializable) tenders);
        writeTendersToCSV (outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + "-" + EprocFetcher.RUN_TAG, tenders);
        writeTendersToCSV (outputDir + File.separator + statusPrefix + TENDER_FILENAME_PREFIX + "-" + LATEST_TAG, tenders);
    }
}
