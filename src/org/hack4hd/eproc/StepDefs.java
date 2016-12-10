package org.hack4hd.eproc;

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

import java.io.*;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class StepDefs {
    private WebDriver driver;
    public static String BASE_DIR;
    private static String BROWSER_NAME;

    private static String DEFAULT_BASE_DIR = System.getProperty ("user.home") + File.separator + "epadd-test";
	private static Log log = LogFactory.getLog(StepDefs.class);
	static Properties VARS;
    private static String EPADD_TEST_PROPS_FILE = System.getProperty("user.home") + File.separator + "epadd.test.properties";

    private String opsystem = System.getProperty("os.name");
    private Process epaddProcess = null;
    private Stack<String> tabStack = new Stack<>();
    public static String testStatus = "1...2...3", testStatusColor = "rgba(10,140,10,0.8)";
	private String screenshotsDir;

    public boolean runningOnMac() { return System.getProperty("os.name").startsWith("Mac"); }

    public StepDefs() {
		VARS = new Properties();

		File f = new File(EPADD_TEST_PROPS_FILE);
		if (f.exists() && f.canRead()) {
			log.info("Reading configuration from: " + EPADD_TEST_PROPS_FILE);
			try {
				InputStream is = new FileInputStream(EPADD_TEST_PROPS_FILE);
				VARS.load(is);
			} catch (Exception e) {
				print_exception("Error reading epadd properties file " + EPADD_TEST_PROPS_FILE, e, log);
			}
		} else {
			log.warn("ePADD properties file " + EPADD_TEST_PROPS_FILE + " does not exist or is not readable");
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
	public void waitFor(int time) throws InterruptedException {
		TimeUnit.SECONDS.sleep(time);
	}

	// @Given("^I enter (.*) into input field with name \"(.*?)\"$")
	public void enterValueInInputField( String fieldName, String inputValue) throws InterruptedException {
		inputValue = resolveValue(inputValue);
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

	// @Then("I close ePADD$")
	public void closeEpadd() throws IOException, InterruptedException {
        updateTestStatus("Closing ePADD");
		if (epaddProcess == null)
			return;
		epaddProcess.destroy();
	}

	// @Given("I open ePADD$")
	public void openEpadd(String mode) throws IOException, InterruptedException {
		// we'll always launch using epadd-standalone.jar
        updateTestStatus("Starting ePADD");

		String errFile = System.getProperty("java.io.tmpdir") + File.separator + "epadd-test.err.txt";
		String outFile = System.getProperty("java.io.tmpdir") + File.separator + "epadd-test.out.txt";
        String cmd = VARS.getProperty ("cmd");
        if (cmd == null) {
            log.warn ("Please confirm cmd in " + EPADD_TEST_PROPS_FILE);
            throw new RuntimeException ("no command to start epadd");
        }

        cmd = "java -Depadd.mode=" + mode +  " -Depadd.base.dir=" + BASE_DIR + " " + cmd;
        cmd = cmd + " --no-browser-open"; // we'll open our own browser
        ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));

//		ProcessBuilder pb = new ProcessBuilder("java", "-Xmx2g", "-jar", "epadd-standalone.jar", "--no-browser-open");
		pb.redirectError(new File(errFile));
		pb.redirectOutput(new File(outFile));
		log.info ("Sending epadd output to: " + outFile);
		epaddProcess = pb.start();
		log.info ("Started ePADD");
	}

	// @Then("CSS element \"(.*)\" should have value (.*)$")
	public void verifyEquals(String selector, String expectedValue) {
		expectedValue = resolveValue(expectedValue);
		String actualText = driver.findElement(By.cssSelector(selector)).getText();
	    
		if (!actualText.equals(expectedValue)) {
			log.warn ("ACTUAL text for CSS selector " + selector + ": " + actualText + " EXPECTED: " + expectedValue);
			throw new RuntimeException();
		}
		log.info ("Found expected text for CSS selector " + selector + ": " + actualText);
		
	}

	// @Then("CSS element \"([^\"]*)\" should contain (.*)$")
	public void verifyContains(String selector, String expectedValue) {
		expectedValue = resolveValue(expectedValue);
		String actualText = driver.findElement(By.cssSelector(selector)).getText();
		actualText = actualText.toLowerCase();
		expectedValue = expectedValue.toLowerCase();
		if (!actualText.contains(expectedValue)) {
			log.warn ("ACTUAL text for CSS selector " + selector + ": " + actualText + " EXPECTED TO CONTAIN: " + expectedValue);
			throw new RuntimeException();
		}
		log.info ("Found expected text for CSS selector " + selector + ": " + actualText);
	}


	// @Then("CSS element \"([^\"]*)\" should start with a number > 0")
	public void verifyStartsWithNumberGT0(String selector) {
		String actualText = driver.findElement(By.cssSelector(selector)).getText();
		actualText = actualText.trim();
		char ch = actualText.charAt(0);
		if (Character.isDigit(ch) && ch > '0') { // the number can't start with 0
			// its ok
		} else {
			log.warn ("ACTUAL text " + actualText + " was expected to start with a number > 0");
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
		linkText = resolveValue(linkText);
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
        script +=  "document.body.innerHTML += '<div id=\"test-status\" style=\"font-family:sans-serif,serif;position:fixed;bottom:0px; width:100%; text-align:center; font-size:18px; background-color:" + testStatusColor + ";color:white;border-top: solid 2px black; padding: 5px;\">Test status: " + testStatus + "</div>';";
        try { ((JavascriptExecutor) driver).executeScript(script); } catch (Exception e) { }
    }

    // @Then("^I wait for the page (.*?) to be displayed within (\\d+) seconds$")
	public void waitForPageToLoad(String url, int time) {
		url = resolveValue(url);
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
		buttonText = resolveValue(buttonText);
		
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

	// @Then("^I check for ([<>]*) *(\\d+) messages on the page$")
	public void checkMessagesOnBrowsePage(String relation, int nExpectedMessages) {
		relation = relation.trim();
		int nActualMessages = nMessagesOnBrowsePage();
		log.info ("checking for " + relation + " " + nExpectedMessages + " messages, got " + nActualMessages);
		if ("".equals(relation) && !(nActualMessages == nExpectedMessages))
			throw new RuntimeException("Expected " + nExpectedMessages + " found " + nActualMessages);
		if (">".equals(relation) && !(nActualMessages > nExpectedMessages))
			throw new RuntimeException("Expected >" + nExpectedMessages + " found " + nActualMessages);
		if ("<".equals(relation) && !(nActualMessages < nExpectedMessages))
			throw new RuntimeException("Expected <" + nExpectedMessages + " found " + nActualMessages);
	}

	// @Then("I check for ([<>]*) *(\\d+) highlights on the page")
	public void checkHighlights(String relation, int nExpectedHighlights) {
		Collection<WebElement> highlights = driver.findElements(By.cssSelector(".muse-highlight"));
		highlights.addAll(driver.findElements(By.cssSelector(".hilitedTerm"))); // could be either of these classes used for highlighting
		int nHighlights = highlights.size();

		log.info ("checking for " + relation + " " + nExpectedHighlights + " messages, got " + nHighlights);
		if ("".equals(relation) && !(nHighlights == nExpectedHighlights))
			throw new RuntimeException("Expected " + nExpectedHighlights + " found " + nHighlights);
		if (">".equals(relation) && !(nHighlights > nExpectedHighlights))
			throw new RuntimeException("Expected >" + nExpectedHighlights + " found " + nHighlights);
		if ("<".equals(relation) && !(nHighlights < nExpectedHighlights))
			throw new RuntimeException("Expected <" + nExpectedHighlights + " found " + nHighlights);
	}

	// @And("I check that \"(.*)\" is highlighted")
	public void checkHighlighted(String termToBeHighighted) {
		Collection<WebElement> highlights = driver.findElements(By.cssSelector(".muse-highlight"));
		highlights.addAll(driver.findElements(By.cssSelector(".hilitedTerm"))); // could be either of these classes used for highlighting
		for (WebElement e: highlights)
			if (termToBeHighighted.equals(e.getText())) {
				log.info ("highlighted term " + termToBeHighighted + " found");
				return;
			}
		String message = "highlighted term " + termToBeHighighted + " not found!";
		log.warn (message);
		throw new RuntimeException(message);
	}

		// check for some messages in another tab, then close it
	// @Then("some messages should be displayed in another tab$")
	public void someMessagesShouldBeDisplayed() throws InterruptedException {
		String parentWindow = driver.getWindowHandle();
		Set<String> handles = driver.getWindowHandles();
		for (String windowHandle : handles) {
			if (!windowHandle.equals(parentWindow)) {
				driver.switchTo().window(windowHandle);
				int nMessages = nMessagesOnBrowsePage();
				if (nMessages <= 0) {
					throw new RuntimeException("Error: No messages on browse page");
				} else {
					log.info (nMessages + " messages on the browse page");
				}
				driver.close();
			}
		}
		driver.switchTo().window(parentWindow);
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

	// @Given("I mark all messages \"Do not transfer\"")
	public void markDNT() throws InterruptedException {
		WebElement e = driver.findElement(By.id("doNotTransfer"));

		if (!e.getAttribute("class").contains("flag-enabled")) {
			driver.findElement(By.id("doNotTransfer")).click();
			waitFor(1);
		}

		driver.findElement(By.id("applyToAll")).click();
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
		waitFor (2);
		select.selectByVisibleText(value);
	}

	public void getTendersOnPage() throws InterruptedException, IOException {
        List<WebElement> rows = driver.findElements(By.xpath("//table[@id='eprocTenders:browserTableEprocTenders']//tr"));
        int nRow = 0;
        for (WebElement row: rows) {
            List<WebElement> cols = row.findElements(By.tagName("td"));
            if (cols.size() < 10)
                continue;

            int nCol = 0;
            String tenderId = "<NONE>", tenderDir = "NONE";
            List<String> textCols = new ArrayList<>();
            for (WebElement col: cols) {
                String t = col.getText();
                System.out.println ("col " + (++nCol) + ": " + t);
                if (nCol == 3) {
                    tenderId = t;
                    tenderDir = tenderId;
//                    tenderDir = tenderId.replaceAll("/", "@");
                    tenderDir += File.separator;
                    new File(tenderId).mkdirs();
                }
                textCols.add(t);
            }

            WebElement lastCol = cols.get (cols.size()-1);
            List<WebElement> subPages = lastCol.findElements(By.tagName("a"));

            int nLinks = 0;
            for (WebElement subPage: subPages) {
//                link.click();
                String url = subPage.getAttribute("href");

                // open in a new tab. but get info on that tab and then close it.
                // max 2 tabs will be open at a time

                ((JavascriptExecutor) driver).executeScript("window.open('" + url + "','_blank');");
                ArrayList<String> tabs = new ArrayList<> (driver.getWindowHandles());
                String subPageTitle = ((RemoteWebElement) subPage).findElementByTagName("img").getAttribute("title");

                // save the page
                driver.switchTo().window(tabs.get(1));
                String pageSource = driver.getPageSource();
                String htmlFile = tenderDir + subPageTitle + ".html";
                PrintWriter pw = new PrintWriter(new FileOutputStream(htmlFile)); pw.println (pageSource); pw.close();

                boolean isDownloadDocsSubpage = subPageTitle.contains ("Download Tender Documents");
                log.info ("Subpage #" + (++nLinks) + ":" + "Download subpage = " + isDownloadDocsSubpage + " " + url);

                // if download docs subpage, also print out the links for the docs
                if (isDownloadDocsSubpage) {
                    List<WebElement> downloadLinks = driver.findElements(By.xpath("//td/a"));
                    for (WebElement downloadLink : downloadLinks) {
                        String href = downloadLink.getAttribute("href");
                        if (href != null && href.contains("?")) // only log links with ?s for url param. otherwise https://eproc.karnataka.gov.in/eprocurement/login.seam also shows up as a download link
                            log.info("Download link: " + href);
                    }
                }

                waitFor(1);
                driver.close();
                driver.switchTo().window(tabs.get(0)); // explicitly switching back to the original tab is important
            }
            log.info ("Completed row # " + (++nRow) + " TenderID: " + tenderId + "\n\n---------\n\n");
        }
    }

	// @Then("I verify the folder (.*) does not exist$")
	public void checkFolderDoesNotExist(String folderName) throws InterruptedException, IOException {
		folderName = resolveValue(folderName);
		if (new File(folderName).exists()) {
			throw new RuntimeException ("Folder " + folderName + " is not expected to exist, but it does!");
		}
		log.info ("Good, folder " + folderName + " does not exist");
	}

	// @Then("I verify the folder (.*) exists$")
	public void checkFolderExists(String folderName) throws InterruptedException, IOException {
		folderName = resolveValue(folderName);
		if (!new File(folderName).exists()) {
			throw new RuntimeException ("Folder " + folderName + " is expected to exist, but it does not!");
		}
		log.info ("Good, folder " + folderName + " exists");
	}

	// if the value is <abc> then we read the value of property abc in the hook. otherwise we use it as is.
	public String resolveValue(String s) {
		if (s == null)
			return null;
		s = s.trim(); // strip spaces before and after
		if (s.startsWith("<") && s.endsWith(">"))
			s = VARS.getProperty(s.substring(1, s.length()-1));
		if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) // strip quotes -- if "abc", simply make it abc
			s = s.substring(1, s.length()-1);
		return s;
	}

}
