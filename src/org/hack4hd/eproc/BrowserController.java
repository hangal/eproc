package org.hack4hd.eproc;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BrowserController {
    private WebDriver driver;
    public static String BASE_DIR;
    private static String BROWSER_NAME;

    private static String DEFAULT_BASE_DIR = System.getProperty("user.home") + File.separator + "eproc-test";
    private static Log log = LogFactory.getLog(BrowserController.class);
    static Properties VARS;
    private static String EPROC_TEST_PROPS_FILE = System.getProperty("user.home") + File.separator + "eproc.test.properties";

    private Stack<String> tabStack = new Stack<>();
    public static String testStatus = "Testing....", testStatusColor = "rgba(10,140,10,0.8)";
    private String screenshotsDir;

    // Create a trust manager that does not validate certificate chains
    // because the eproc certs cause the download for the https URL on the subpages docs to fail otherwise
    static TrustManager[] trustAllCerts;
    static {
        try {
            // Install the all-trusting trust manager
            trustAllCerts    = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null;}
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch(Exception e) { }
    }

    public boolean runningOnMac() { return System.getProperty("os.name").startsWith("Mac"); }

    public BrowserController() {
		VARS = new Properties();

		File f = new File(EPROC_TEST_PROPS_FILE);
		if (f.exists() && f.canRead()) {
			log.info("Reading configuration from: " + EPROC_TEST_PROPS_FILE);
			try {
				InputStream is = new FileInputStream(EPROC_TEST_PROPS_FILE);
				VARS.load(is);
			} catch (Exception e) {
				print_exception("Error reading epadd properties file " + EPROC_TEST_PROPS_FILE, e, log);
			}
		} else {
			log.warn("ePADD properties file " + EPROC_TEST_PROPS_FILE + " does not exist or is not readable");
		}

        for (String key: VARS.stringPropertyNames()) {
            String val = System.getProperty (key);
            if (val != null && val.length() > 0)
                VARS.setProperty(key, val);
        }

        BASE_DIR = VARS.getProperty("epadd.test.dir");
        if (BASE_DIR == null)
            BASE_DIR = DEFAULT_BASE_DIR;

        new File(BASE_DIR).mkdirs();
        screenshotsDir = BASE_DIR + File.separator + "screenshots";
        new File(screenshotsDir).mkdirs();

        log.info ("Base dir for this test run is: " + BASE_DIR);
    }

    public static String stackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter(0);
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.close();
        return sw.getBuffer().toString();
    }

    public static void print_exception(String message, Throwable t, Log log)
    {
        String trace = stackTrace(t);
        String s = message + "\n" + t.toString() + "\n" + trace;
        if (log != null)
            log.warn(s);
        System.err.println(s);
    }

	// @Given("^I navigate to \"(.*?)\"$")
	public void openURL(String url) {
        driver.navigate().to(url);
	}

	// @Given("^I wait for (\\d+) sec$")
	public void waitFor(int time) {
        try { TimeUnit.SECONDS.sleep(time); }
        catch (Exception e) { }
	}

	// @Given("^I enter (.*) into input field with name \"(.*?)\"$")
	public void enterValueInInputField(String fieldName, String inputValue) throws InterruptedException {
		try {
			WebElement inputField = driver.findElement(By.name(fieldName));
			inputField.sendKeys(inputValue);
		} catch (Exception e) {
			throw new RuntimeException ("Unable to find an input field to enter value in: (" + inputValue + ") " + "field: " + fieldName + " page: " + driver.getCurrentUrl());
		}
	}

	// @Then("I navigate back$")
	public void navigateBack() {
		driver.navigate().back();
	}

	// @Then("CSS element \"(.*)\" should have value (.*)$")
	public void verifyEquals(String selector, String expectedValue) {
		String actualText = driver.findElement(By.cssSelector(selector)).getText();
	    
		if (!actualText.equals(expectedValue)) {
			log.warn ("ACTUAL text for CSS selector " + selector + ": " + actualText + " EXPECTED: " + expectedValue);
			throw new RuntimeException();
		}
		log.info ("Found expected text for CSS selector " + selector + ": " + actualText);
		
	}

	// @Then("CSS element \"([^\"]*)\" should contain (.*)$")
	public void verifyContains(String selector, String expectedValue) {
		String actualText = driver.findElement(By.cssSelector(selector)).getText();
		actualText = actualText.toLowerCase();
		expectedValue = expectedValue.toLowerCase();
		if (!actualText.contains(expectedValue)) {
			log.warn ("ACTUAL text for CSS selector " + selector + ": " + actualText + " EXPECTED TO CONTAIN: " + expectedValue);
			throw new RuntimeException();
		}
		log.info ("Found expected text for CSS selector " + selector + ": " + actualText);
	}

	// @Then("^open browser$")
	public void openBrowser() throws MalformedURLException {
        try {
            // String consoleOutputFile = this.getValue("browserConsoleOutputFile");
            // System.setProperty("webdriver.log.file", consoleOutputFile + "-" + this.getValue("browser") + ".txt");

            BROWSER_NAME = VARS.getProperty ("browser");

            if (BROWSER_NAME == null)
                BROWSER_NAME = "chrome";
            if ("firefox".equalsIgnoreCase(BROWSER_NAME)) {
                driver = new FirefoxDriver();
            } else if ("chrome".equalsIgnoreCase(BROWSER_NAME)) {
                if (runningOnMac()) {
                    String macDriver = VARS.getProperty ("webdriver.chrome.driver");
                    if (macDriver == null)
                        macDriver = "/Users/hangal/workspace/epadd-launcher/src/test/resources/chromedriver";
                    System.setProperty("webdriver.chrome.driver", macDriver);
                } else {
                    System.err.println ("WARNING!!!! Chrome driver is only specified for Mac?");
                }
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--always-authorize-plugins=true"); // to allow flash - c.f. http://stackoverflow.com/questions/28804247/how-to-enable-plugin-in-chrome-browser-through-capabilities-using-web-driver
                driver = new ChromeDriver(options);
            } else if ("ie".equalsIgnoreCase(BROWSER_NAME)) {
                driver = new InternetExplorerDriver();
            }
            driver.manage().deleteAllCookies();
            driver.manage().window().maximize();
        } catch (Exception e) {
            print_exception("Error opening browser", e, log);
        }
    }

    public void closeBrowser() {
		driver.close();
	}

	// @Then("^I take full page screenshot called \"(.*?)\"$")
	public void takeScreenshot(String pageName) throws IOException {
        String savedStatus = testStatus;
        updateTestStatus("Taking screenshot: " + pageName);
		String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
		String stamp = timestamp + ".png";
		Dimension saved = driver.manage().window().getSize();
//		driver.manage().window().setSize(new Dimension(1280, 2000));
		File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		FileUtils.copyFile(scrFile, new File(screenshotsDir + File.separator + BROWSER_NAME + "-" + pageName + "-" + stamp));
//		driver.manage().window().setSize(saved);
        updateTestStatus(savedStatus);
	}

	public void visitAndTakeScreenshot(String url) throws IOException, InterruptedException {
		visitAndTakeScreenshot(url, 1);
	}

	public void visitAndTakeScreenshot(String url, int waitSecs) throws IOException, InterruptedException {
        openURL (url);
        int idx = url.lastIndexOf ("/");
        String page = (idx >= 0) ? url.substring (idx+1) : url;
		Thread.sleep (waitSecs * 1000);
        takeScreenshot(page);
    }

	// @Then("I verify that I am on page \"(.*?)\"$")
	public void verifyURL(String expectedURL) {
		String currentURL = driver.getCurrentUrl();
		if (!currentURL.contains(expectedURL))
			throw new RuntimeException("Expected URL: " + expectedURL + " actual URL: " + currentURL);
	}

	public void clickOn(String linkText) throws InterruptedException {
		clickOn ("", linkText);
	}

	public void enterPrompt (String value) {
        Alert alert = driver.switchTo().alert();
        alert.sendKeys(value);
        alert.accept();
    }

    // @Given("I find CSS element \"(.*)\" and click on it$")
    public void clickOnCSS(String cssSelector) throws InterruptedException {
        // this could hit any element with the text! e.g. a button, an a tag, or even a td tag!
        String prevURL = driver.getCurrentUrl();
        waitFor (2);
        WebElement e = driver.findElement(By.cssSelector(cssSelector));
        if (e == null) {
            log.warn("ERROR: CSS element " + cssSelector + " not found!");
            throw new RuntimeException();
        }
        e.click();
        waitFor (2);

        String newURL = driver.getCurrentUrl();
        if (!prevURL.equals(newURL))
            updateTestStatus(); // new page, so status has to be refreshed on it
    }

	// @Given("I find CSS element \"(.*)\" and click on it$")
	public void clickOnXpath(String xpathSelector) throws InterruptedException {
		// this could hit any element with the text! e.g. a button, an a tag, or even a td tag!
		String prevURL = driver.getCurrentUrl();
		waitFor (2);
		WebElement e = driver.findElement(By.xpath(xpathSelector));
		if (e == null) {
			log.warn("ERROR: xpath element " + xpathSelector + " not found!");
			throw new RuntimeException();
		}
		e.click();
		waitFor (2);

		String newURL = driver.getCurrentUrl();
		if (!prevURL.equals(newURL))
			updateTestStatus(); // new page, so status has to be refreshed on it
	}

	// will click on the link with the exact linkText if available; if not, on a link containing linkText
	// linkText is case insensitive
	// can use as:
	// I click on "Search" --> searches button, link, td tags with this text (or their sub-elements), in that order
	// or
	// I click on button "Search"
	// @Given("I click on (.*) *\"(.*?)\"$")
	public void clickOn(String elementType, String linkText) throws InterruptedException {
        // testStatus = "Clicking on" + ((elementType != null) ? elementType + " " : "") + linkText;

		elementType = elementType.trim(); // required because linkText might come as "button " due to regex matching above
		linkText = linkText.toLowerCase();
		WebElement e = null;

		// we'll look for linkText in a few specific tags, in this defined order
		// sometimes the text we're looking for is under a further element, like <a><p>...</p></a>
		String searchOrderEType[] = (elementType.length() != 0) ? new String[]{elementType, elementType + "//*"} : new String[]{"button","div//button","div//*", "a", "td", "button//*","a//*", "td//*","span"};

		// prefer to find an exact match first if possible
		// go in order of searchOrderEtype
		// be careful to ignore invisible elements
		// be case-insensitive
		for (String s: searchOrderEType) {
			String xpath = "//" + s + "[translate(text(),  'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz') = '" + linkText + "')]";
			try { e = driver.findElement(By.xpath(xpath)); } catch (Exception e1) { } // ignore the ex, we'll try to find a link containing it
			if (e != null && !e.isDisplayed())
				e = null; // doesn't count if the element is not visible
			if (e != null)
				break;
		}

		// no exact match? try to find a contained match, again in order of searchOrderEtype
		if (e == null) {
			for (String s: searchOrderEType) {
				String xpath = "//" + s + "[contains(translate(text(),  'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + linkText + "')]";
				try { e = driver.findElement(By.xpath(xpath)); } catch (Exception e1) { } // ignore the ex, we'll try to find a link containing it
				if (e != null && !e.isDisplayed())
					e = null; // doesn't count if the element is not visible
				if (e != null)
					break;
			}
		}

        String prevURL = driver.getCurrentUrl();

        // ok, we have an element to click on?
		if (e != null) {
			// color the border red of the selected element to make it easier to understand what is happening
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true); arguments[0].style.border = '2px solid red';", e);
			log.info ("Clicking on (" + e.getTagName() + ") containing " + linkText);
			waitFor(1);
            Actions actions = new Actions (driver);
            actions.moveToElement(e);
            waitFor (1);
			e.click(); // seems to be no way of getting text of a link through CSS
			waitFor(1); // always wait for 1 sec after click

            // wait for next page to load by checking its readyState, up to 30 secs
            new WebDriverWait(driver, 30).until((ExpectedCondition<Boolean>) wd ->
                    ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete")); // from http://stackoverflow.com/questions/15122864/selenium-wait-until-document-is-ready

            String newURL = driver.getCurrentUrl();
            if (!prevURL.equals(newURL))
                updateTestStatus();
        } else
			throw new RuntimeException ("Unable to find an element to click on: (" + elementType + ") " + linkText + " page: " + driver.getCurrentUrl());
	}

    void updateTestStatus() {
        updateTestStatus(testStatus);
    }

    void updateTestStatus(String status) {
        testStatus = status;
        String script = "var e123 = document.getElementById('test-status'); if (e123 != null) { e123.remove(); }";
      //  script +=  "document.body.innerHTML += '<div id=\"test-status\" style=\"font-family:sans-serif,serif;position:fixed;bottom:0px; width:100%; text-align:center; font-size:18px; background-color:" + testStatusColor + ";color:white;border-top: solid 2px black; padding: 5px;\">Test status: " + testStatus + "</div>';";
     //   try { ((JavascriptExecutor) driver).executeScript(script); } catch (Exception e) { }
    }

    // @Then("^I wait for the page (.*?) to be displayed within (\\d+) seconds$")
	public void waitForPageToLoad(String url, int time) {
		long startMillis = System.currentTimeMillis();
		WebDriverWait wait = new WebDriverWait(driver, time);
		try {
			wait.until(ExpectedConditions.urlMatches(url));
            updateTestStatus();
        } catch (org.openqa.selenium.TimeoutException e) {
			throw new RuntimeException (url + " did not open in " + time + " seconds. Exception occurred: ", e);
		}

		log.info ("Page " + url + " loaded in " + (System.currentTimeMillis() - startMillis) + "ms");
	}

	// waits for button containing the given buttonText to appear within time seconds
	// @Then("^I wait for button (.*?) to be displayed within (\\d+) seconds$")
	public void waitForButton(String buttonText, int time) {

		long startMillis = System.currentTimeMillis();
		WebDriverWait wait = new WebDriverWait(driver, time);
		try {
			buttonText = buttonText.toLowerCase();
			String xpath = "//*[contains(translate(text(),  'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + buttonText + "')]";

			driver.findElement(By.xpath(xpath)).getText();
			wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath))); // case insensitive match! see
		} catch (org.openqa.selenium.TimeoutException e) {
			throw new RuntimeException ("Button text" + buttonText + " was not found in " + time + " seconds. Exception occured: ", e);
		}

		log.info ("Button " + buttonText + " clickable in " + (System.currentTimeMillis() - startMillis) + "ms");
	}

	private int nMessagesOnBrowsePage() {
		String num = driver.findElement(By.id("pageNumbering")).getText();
		// num will be "x/y", e.g. something like 123/312. Extract the "312" part of it
		String totalNumberOfEmails = num.substring(num.indexOf("/")).replace("/", "");
		int n = -1;
		try { n = Integer.parseInt(totalNumberOfEmails); } catch (Exception e) { }
		return n;
	}

	// @Given("I switch to the \"(.*)\" tab$")
	public void switchToTab(String title) throws InterruptedException {
        title = title.toLowerCase();
		String parentWindow = driver.getWindowHandle();
		Set<String> handles = driver.getWindowHandles();
		for (String windowHandle : handles) {
			if (!windowHandle.equals(parentWindow)) {
				driver.switchTo().window(windowHandle);
                String tabTitle = driver.getTitle();
                if (tabTitle == null)
                    continue;
                tabTitle = tabTitle.toLowerCase();
				if (title.equals(tabTitle)) {
					tabStack.push(parentWindow);
					return;
				}
			}
		}
		log.warn ("Error: tab with title " + title + " not found!");
		// title not found? return to parentWindow
		driver.switchTo().window(parentWindow);
	}

	// @Given("I close tab")
	public void closeTab() throws InterruptedException {
		driver.close();
		// need to explicitly switch to last window, otherwise driver will stop working
		if (tabStack.size() > 0) {
			String s = tabStack.pop();
			driver.switchTo().window(s);
		}
	}

	// @Given("I switch to the previous tab")
	public void switchTabBack() throws InterruptedException {
		if (tabStack.size() < 1) {
			log.warn ("Warning: trying to pop tab stack when it is empty!");
			return;
		}

		String lastWindow = tabStack.pop();
		switchToTab (lastWindow);
	}

	// @Given("I set dropdown \"(.*?)\" to \"(.*?)\"$")
	public void dropDownSelection(String cssSelector, String value) throws InterruptedException {
        WebElement element = driver.findElement(By.cssSelector(cssSelector));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true); arguments[0].style.border = '2px solid red';", element);
		Select select = new Select(element);
        waitFor (2);
        select.selectByVisibleText(value);
	}

	// @Given("I set dropdown \"(.*?)\" to \"(.*?)\"$")
	public void dropDownSelectionByXpath(String xpathSelector, String value) throws InterruptedException {
		WebElement element = driver.findElement(By.xpath (xpathSelector));
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true); arguments[0].style.border = '2px solid red';", element);
		Select select = new Select(element);
		waitFor (1);
		select.selectByVisibleText(value);
        waitFor (1);
	}

    // look for <td> elements whose text contains the fieldname and return the value of the next field
	public String getValueOfNextField (String fieldName) {
        try {
            List<WebElement> titleElements = driver.findElements(By.xpath("//td[text()[contains(.,'" + fieldName + "')]]"));
            if (titleElements == null)
                return null;
            // sanity check
            if (titleElements.size() > 1)
                log.warn ("More than 1 hit for title " + fieldName + " (got " + titleElements.size() + ")");

            // read the first element even if > 1 hit
            if (titleElements.size() > 0) {
                WebElement titleElement = titleElements.get(0);
                if (titleElement != null) {
                    WebElement nextElement = titleElement.findElement((By.xpath("following-sibling::*[1]")));
                    if (nextElement == null) {
                        log.warn ("Strange... no next element found for field " + fieldName);
                    }
                    return nextElement.getText();
                }
            }
        } catch (Exception e) { }
        return null;
    }

	/** given the row xpath, goes into each subpage linked to from in the last column of that row and fetches the subpage, as well as any download links on it */
	public void downloadSubPages (String rowXpath, Tender tender, String tenderDir) throws IOException {
        // usually, the page titles are: View Notice Inviting Tender details, Download Tender Documents, View Addendum for this tender, View Corrigendum for this tender, View Selected Supplier/s for this Tender
        // I believe multiple sub pages with the same title are possible, so we'll be extra careful, using subPageLowerCaseTitlesSeen
        // note: The "/" in View Selected Supplier/s for this Tender will be replaced by "_"

        // each sub page
        String subPagesXpath = rowXpath + "/td[" + EprocFetcher.N_COLS_IN_TENDER_ROW + "]/a"; // cell 11 is the last cell in the row, and it contains all the links to the sub pages
        int nSubPages = 0;
        try { nSubPages = driver.findElements(By.xpath(subPagesXpath)).size(); }
        catch (Exception e) { }

        FileNameManager subPageNameManager = new FileNameManager();
        FileNameManager blobNameManager = new FileNameManager();

        nextSubPage:
        for (int i = 0; i < nSubPages; i++) {

            // very important: we need to refresh the page!
            // otherwise, due to a probable bug in eproc, the bid evaluation results page intermittently omits the data we're interested in!
            // see docs/screenshots/bid-eval-results-bug.png
            // refreshing the page before clicking on each subpage seems to solve the problem

            driver.navigate().refresh();

            String xpath = subPagesXpath + "[" + (i+1) + "]"; // look for the i+1'th sup page (xpath numbering starts from 1)
            WebElement subPageLink = driver.findElement(By.xpath(xpath));
            // use the title attribute of the img element inside the subpage link as the subpage title.
            // however, massage it to avoid conflicts, get rid of "/" embedded in it, etc.
            String subPageTitle = ((RemoteWebElement) subPageLink).findElementByTagName("img").getAttribute("title");
            subPageTitle = subPageNameManager.registerAndDisambiguate(subPageTitle);
            tender.addSubPage(subPageTitle);

            // open in a new tab. but get info on that tab and then close it.
            // max 2 tabs will be open at a time
            // String url = subPageLink.getAttribute("href");
            // ((JavascriptExecutor) driver).executeScript("window.open('" + url + "','_blank');");
            // set the target to _blank to make it open in a new page and click on it
            ((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute(arguments[1], arguments[2])", subPageLink, "target", "_blank");
            subPageLink.click();
            waitFor (1);
            // now subpage should open in a new tab; switch to it

            ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());

            // save the page to a file
            if (tabs.size() <= 1) {
                // saw only 1 tab once
                log.warn("Only 1 tab open?? tender #= " + tender.number + " #subpage = " + i);
                continue;
            }

            {
                driver.switchTo().window(tabs.get(1));
                updateTestStatus();
                String pageSource = driver.getPageSource();
                String htmlFile = tenderDir + subPageTitle + ".html";
                PrintWriter pw = new PrintWriter(new FileOutputStream(htmlFile));
                pw.println(pageSource);
                pw.close();
            }

            // on these subpages, there is a td.pageHeading that contains the title of the page
            String subPageHeading = null;
            try { subPageHeading = driver.findElement(By.cssSelector(EprocFetcher.SUBPAGE_HEADING_CSS)).getText(); }
            catch (Exception e) { }

            // if its the tender details page, pick up some of the structured fields in it. See screenshots/tender-details.png for example
            if ("Tender details".equalsIgnoreCase (subPageHeading)) {
                tender.hasTenderDetails = true;
                tender.financialBidType = getValueOfNextField ("Type of Quotation");
                tender.evaluationType = getValueOfNextField ("Tender Evaluation Type");
                tender.department = getValueOfNextField ("Department");
                tender.bidValidityPeriod = getValueOfNextField ("Bid Validity Period");
                tender.noOfCalls = getValueOfNextField ("No Of Calls");
                tender.denominationType = getValueOfNextField ("Denomination Type");
            }

            // if its the bid eval results page, pick up some of the structured fields in it. See screenshots/bid-eval-results.png for example
            if ("Bid Evaluation Results".equals (subPageHeading)) {

                tender.hasBidEvaluationResults = true;
                tender.financialBidType = getValueOfNextField ("Financial Bid Type");
                tender.selectedSupplier = getValueOfNextField ("Selected Supplier");
                tender.selectedCompanyName = getValueOfNextField ("Company Name");
                tender.bidAmountInFigures = getValueOfNextField ("Bid Amount ( Rs. In figures"); // darn spelling and caps in eproc -- we better be exact!!!!
                tender.bidAmountInWords = getValueOfNextField ("Bid Amount ( Rs. In words");
            }

            log.info("Tender " + tender.number + ", Subpage #" + (i+1) + ":" + subPageHeading);

            // if download links on subpage, also fetch those, if they don't already exist

            // example of what a downloadable file looks like link: https://eproc.karnataka.gov.in/eprocurement/secure/FileViewer?uuid=4980b5ec-5673-47a0-8e4e-6cdc0e65a4ae&workspace=departments&repositoryType=TERTIARY
            List<WebElement> downloadLinks;
            try { downloadLinks = driver.findElements(By.xpath("//td/a[contains(@href, 'FileViewer?uuid')]")); }
            catch (Exception e) { continue nextSubPage; } // if no download links, just continue to the next page

            if (EprocFetcher.FETCH_ALL_BLOBS || EprocFetcher.FETCH_ONLY_TEXT_BLOBS) {
                // ok, we do have some download links, let's get them
                for (WebElement downloadLink : downloadLinks) {
                    String href = downloadLink.getAttribute("href");

                    // the filename is in the text part of the link
                    String blobName = downloadLink.getText();

                    if (EprocFetcher.FETCH_ONLY_TEXT_BLOBS && !Util.isTextBlob(blobName))
                        continue;

                    blobName = blobNameManager.registerAndDisambiguate(blobName);
                    log.info("Download link for file: " + blobName + " href: " + href);
                    File f = new File(tenderDir + File.separator + blobName);
                    tender.addBlob(blobName, href);
                    if (!f.exists()) {
                        try {
                            // download save, convert to text
                            FileUtils.copyURLToFile(new URL(href), f);
                            String text = Util.blobToText(f.getAbsolutePath());
                            Util.writeStringToFile(text, f.getAbsolutePath() + ".txt");
                        } catch (Exception e) {
                            Util.print_exception("Error trying to extract text from " + f.getAbsolutePath(), e, log);
                        }
                    } else
                        log.info("Skipping blob: " + f.getAbsolutePath() + " because it already exists");
                }
            }

            // all done for this subpage
            waitFor(1);
            driver.close();
            driver.switchTo().window(tabs.get(0)); // explicitly switching back to the original tab is important
        }
    }

    /** reads all tenders on the given page */
	public List<Tender> getTendersOnCurrentPage(String rootDir, String status) throws InterruptedException, IOException {
        if (Util.nullOrEmpty(rootDir))
            rootDir = ".";
        List<Tender> tenders = new ArrayList<>();

        String tableXpath = EprocFetcher.TENDERS_TABLE_XPATH;
        int nRows = driver.findElements(By.xpath(tableXpath + "/tbody/tr")).size(); // each row in the body is a tender (need tbody to ensure we exclude the header)

        for (int i = 0; i < nRows; i++) {

            // read the (i+1)'th row (remember xpath numbering starts from 1)
            // we do a fresh findElement each time. If we did a findElement of all the rows outside this loop, we often see problems due to stale elements
            String thisRowXpath = tableXpath + "/tbody/tr[" + (i+1) + "]";
            WebElement row = driver.findElement(By.xpath(thisRowXpath));

            List<WebElement> cols = row.findElements(By.tagName("td"));
            // normally cols will be of size 11
            if (cols.size() < EprocFetcher.N_COLS_IN_TENDER_ROW) {
                log.warn ("unexpected #cols: " + cols.size() + row.getText());
                continue;
            }

            Tender tender = new Tender();
            tender.number = cols.get(2).getText();
            String tenderId = tender.number;
            String tenderDir = rootDir + File.separator + tenderId; // + "-" + status;
            tenderDir += File.separator; // trailing sep to indicate its a directory
            File tenderDirFile = new File(tenderDir);

            // check if this tender dir already exists, or we just use the latest copy in the tender dir if status is unchanged
            boolean readTenderSubPages = true;
            if (EprocFetcher.FETCH_TENDERS_ONLY_IF_STATUS_CHANGED) {
                try {
                    // read existing object
                    String f = tenderDir + EprocFetcher.TENDER_FILENAME_PREFIX + EprocFetcher.LATEST_TENDER_TAG + ".ser";
                    if (new File(f).exists()) {
                        tender = (Tender) Util.readObjectFromFile(f);
                        readTenderSubPages = (!status.equals(tender.currentStatus)); // if status differs from current status, reread subpages
                        log.info ("read cached tender for " + tender.number + ", skipping sub-pages");
                    }
                } catch (Exception e) {
                    readTenderSubPages = true;
                }
            }

            // always update these fields, even if we are not reading sub pages
            tender.currentStatus = status;
            tender.departmentOrLocationCode = cols.get(1).getText();
            tender.title = cols.get(3).getText();
            tender.type = cols.get(4).getText();
            tender.category = cols.get(5).getText();
            tender.subCategory = cols.get(6).getText();
            tender.estimatedValue = cols.get(7).getText();
            tender.NITPublishedDate = cols.get(8).getText();
            tender.lastDateForBidSubmission = cols.get(9).getText();

            tenderDirFile.mkdirs();


            if (readTenderSubPages) {
                log.info("Downloading subpages for tender " + tender.number);
                downloadSubPages(thisRowXpath, tender, tenderDir);
            }

            // always save back tender file, even if we are not reading sub pages
            tenders.add (tender);
            tender.save (tenderDir);

            log.info ("Completed tender in row # " + (i+1) + "/" + nRows + " TenderID: " + tenderId + " Status: " + tender.currentStatus + "\n\n---------\n\n");
        }
        return tenders;
    }
}
