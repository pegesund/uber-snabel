package com.snabel.selenium;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live test that runs against the running Quarkus dev server on port 8081
 * Run this with: mvn test -Dtest=ClaudeCodeLiveTest
 * Make sure the dev server is already running on port 8081!
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClaudeCodeLiveTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String sessionId;
    private static final int APP_PORT = 8081;

    @BeforeAll
    public static void setupDriver() {
        ChromeOptions options = new ChromeOptions();
        // Comment out --headless to see the browser in action
        //options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        System.out.println("=" .repeat(80));
        System.out.println("Claude Code Live Test");
        System.out.println("Testing against http://localhost:" + APP_PORT);
        System.out.println("Make sure the dev server is running!");
        System.out.println("=" .repeat(80));
    }

    @AfterAll
    public static void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    public void testCreateSession() {
        System.out.println("\n[TEST 1] Creating session...");

        // Navigate to the application
        driver.get("http://localhost:" + APP_PORT);

        // Wait for page to load completely
        wait.until(driver -> ((org.openqa.selenium.JavascriptExecutor) driver)
            .executeScript("return document.readyState").equals("complete"));

        // Wait additional time for JavaScript to initialize
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Wait for page to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("description")));

        // Fill in session details
        WebElement descriptionField = driver.findElement(By.id("description"));
        descriptionField.clear();
        descriptionField.sendKeys("Selenium Test Session - " + System.currentTimeMillis());

        WebElement instructionsField = driver.findElement(By.id("instructions"));
        instructionsField.clear();
        instructionsField.sendKeys("You are in a test session. Please respond politely to user messages.");

        // Click create session button
        WebElement createButton = driver.findElement(By.xpath("//button[contains(text(), 'Create Session')]"));
        createButton.click();

        // Wait for upload step to be visible (no popup since we use --continue)
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("step-upload")));

        System.out.println("  ✓ Session created successfully\n");
    }

    @Test
    @Order(2)
    public void testStartWithoutUpload() {
        System.out.println("[TEST 2] Starting Claude Code without upload...");

        // Click "Start Without Upload" button
        WebElement startButton = wait.until(
            ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Start Without Upload')]")
            )
        );
        startButton.click();

        // Wait for alert about Claude Code starting
        wait.until(ExpectedConditions.alertIsPresent());
        Alert alert = driver.switchTo().alert();
        String alertText = alert.getText();
        System.out.println("  Alert: " + alertText);

        assertTrue(alertText.contains("Claude Code started") || alertText.contains("branch"),
                   "Should show Claude Code started message");
        alert.accept();

        // Wait for monitor step to be visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("step-monitor")));

        // Wait for log output to appear
        WebElement logOutput = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.id("log-output"))
        );

        System.out.println("  ✓ Claude Code start initiated");

        // In --print mode, there's no long-running process
        // The session is initialized immediately and ready to receive commands
        // Just verify we can see the log output area
        String logText = logOutput.getText();
        System.out.println("  Initial log output visible: " + !logText.isEmpty());

        System.out.println("  ✓ Claude Code is ready\n");
    }

    @Test
    @Order(3)
    public void testSendCommandAndVerifyResponse() {
        System.out.println("[TEST 3] Sending 'say hello to me' command...");

        // Find command input field
        WebElement commandInput = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.id("command-input"))
        );

        // Skip priming - JavaScript validates empty commands

        // Get the log output before sending actual command to track what's new
        WebElement logOutput = driver.findElement(By.id("log-output"));
        int initialLogLength = logOutput.getText().length();

        // Type the actual command
        commandInput.clear();
        commandInput.sendKeys("say hello to me");

        // Click send or press Enter
        commandInput.sendKeys(Keys.ENTER);

        System.out.println("  ✓ Command sent");
        System.out.println("  Waiting for Claude's response (max 60 seconds)...");

        // Wait up to 60 seconds for Claude to respond with actual content (not just the echo)
        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(60));
        boolean gotResponse = false;
        String errorReason = null;

        try {
            longWait.until(driver -> {
                String logText = logOutput.getText();
                String newContent = logText.substring(Math.min(initialLogLength, logText.length())).toLowerCase();

                // Check if we got stuck - command was sent but no response
                // We no longer echo the command from frontend, only from backend
                boolean hasBackendEcho = newContent.contains("→ say hello to me");
                boolean hasOnlyEcho = hasBackendEcho && logText.length() < initialLogLength + 200;

                if (hasOnlyEcho) {
                    // Still waiting - not stuck yet, just no response
                    return false;
                }

                // Look for Claude's actual response (not just the echo of our command)
                // Claude should respond with "Hello!" or similar greeting
                // Look for the arrow or actual response text
                boolean hasActualResponse = newContent.contains("← hello") ||
                                           newContent.contains("hello!") ||
                                           newContent.contains("hi there") ||
                                           newContent.contains("how can i help") ||
                                           newContent.contains("how can i assist");

                if (hasActualResponse) {
                    System.out.println("  ✓ Claude's actual response detected: found greeting");
                    return true;
                }

                // Also accept if we see substantial new content beyond startup screen
                boolean hasSubstantialResponse = logText.length() > initialLogLength + 500;
                if (hasSubstantialResponse && newContent.contains("help")) {
                    System.out.println("  ✓ Substantial response detected");
                    return true;
                }
                return false;
            });
            gotResponse = true;
        } catch (Exception e) {
            System.out.println("  × Timeout waiting for response");
            errorReason = "Timeout after 60 seconds";
        }

        String finalLog = logOutput.getText();
        System.out.println("  Final log output:");
        System.out.println("  " + finalLog.replace("\n", "\n  "));

        // Additional check: if log ends with just the command echo and nothing more
        if (!gotResponse && finalLog.contains("→ say hello to me")) {
            String afterCommand = finalLog.substring(finalLog.indexOf("→ say hello to me") + "→ say hello to me".length());
            if (afterCommand.trim().isEmpty() || afterCommand.length() < 50) {
                errorReason = "Claude process received command but did not respond. The process may be stuck or stdout is not being captured.";
            }
        }

        if (gotResponse) {
            System.out.println("  ✓ Claude responded successfully\n");
        } else {
            // Print diagnostic info
            System.out.println("  × Claude did not respond");
            System.out.println("  Initial log length: " + initialLogLength);
            System.out.println("  Final log length: " + finalLog.length());
            System.out.println("  Error reason: " + errorReason);
            fail(errorReason != null ? errorReason : "Claude did not respond within 60 seconds");
        }
    }

    @Test
    @Order(4)
    public void testVerifyClaudeIsResponsive() {
        System.out.println("[TEST 4] Verifying Claude is still responsive...");

        WebElement logOutput = driver.findElement(By.id("log-output"));
        int initialLogLength = logOutput.getText().length();

        WebElement commandInput = driver.findElement(By.id("command-input"));
        commandInput.clear();
        commandInput.sendKeys("What is 2+2?");
        commandInput.sendKeys(Keys.ENTER);

        System.out.println("  ✓ Second command sent");
        System.out.println("  Waiting for answer (max 60 seconds)...");

        // Wait for response mentioning the question was submitted AND got an answer
        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(60));

        try {
            longWait.until(driver -> {
                String logText = logOutput.getText();
                String newContent = logText.substring(Math.min(initialLogLength, logText.length())).toLowerCase();

                // First check that command was sent
                boolean commandSent = newContent.contains("what is 2+2") || newContent.contains("sending command");

                if (!commandSent) {
                    return false; // Still waiting for command to appear
                }

                // Look for actual Claude response with arrow prefix
                boolean hasClaudeResponse = newContent.contains("←") &&
                                           (newContent.contains("4") || newContent.contains("four") || newContent.contains("equals"));

                // Also check for mathematical answer format
                boolean hasMathAnswer = newContent.matches(".*\\b4\\b.*") || newContent.contains("four");

                if (hasClaudeResponse || hasMathAnswer) {
                    System.out.println("  ✓ Claude's response detected");
                    return true;
                }
                return false;
            });
            System.out.println("  ✓ Claude is responsive and answering questions\n");
        } catch (Exception e) {
            System.out.println("  × Timeout - Claude may not be responding");
            String logText = logOutput.getText();
            System.out.println("  Current log:");
            System.out.println("  " + logText.replace("\n", "\n  "));
            fail("Claude did not answer the math question within 60 seconds");
        }
    }
}
