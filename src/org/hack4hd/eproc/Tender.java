package org.hack4hd.eproc;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hangal on 12/12/16.
 */
public class Tender implements Serializable {
    private static Log log = LogFactory.getLog(Tender.class);
    public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");

    String currentStatus;
    boolean hasTenderDetails, hasBidEvaluationResults;

    String departmentOrLocationCode, number, title, type, category, subCategory, estimatedValue, NITPublishedDate, lastDateForBidSubmission;
    String typeOfQuotation, evaluationType, department, bidValidityPeriod, noOfCalls, denominationType;

    String financialBidType, selectedSupplier, selectedCompanyName, bidAmountInFigures, bidAmountInWords;
    String allBlobNames, allSubPageNames;  // append the sub-page/blob names into a single string so it can be stored in the csv

    int numBlobs, numSubPages;

    List<String> subPages = new ArrayList<>();
    List<String> blobFiles = new ArrayList<>();

    Map<String, String> otherFields = new LinkedHashMap<>(); // for future use

    public String getId() {
        return number;
    }

    public void addBlob(String f) {
        blobFiles.add (f);
        numBlobs = blobFiles.size();
        allBlobNames = String.join ("|", blobFiles);
    }

    public void addSubPage(String f) {
        subPages.add (f);
        numSubPages = subPages.size();
        allSubPageNames = String.join ("|", subPages);
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

    /** saves tender as .ser and .csv. returns a list of fields as csv */
    public List<String> save (String dir) throws IOException {
        List<String> colList = Util.getInstanceFields(this);
        List<String> colNamesList = Util.getInstanceFieldNames(this);

        colList.add (String.join ("|", colNamesList)); // just for reference, keep the col names list
        colNamesList.add ("Column names");

        log.info ("Writing out columns for tender " + number);

        String dateString = sdf.format (new java.util.Date());
        String filePrefix = "tender-" + dateString;

        // write out .ser version
        Util.writeObjectToFile (dir + File.separator + filePrefix + ".ser", this);

        // write out the CSV file with these fields
        String NEW_LINE_SEPARATOR = "\n";
        CSVFormat csvFileFormat = CSVFormat.DEFAULT.withRecordSeparator(NEW_LINE_SEPARATOR);
        Writer fileWriter = new FileWriter(dir + File.separator + filePrefix + ".csv");
        CSVPrinter csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
        csvFilePrinter.print(colList);
        fileWriter.close();

        return colList;
    }
}
