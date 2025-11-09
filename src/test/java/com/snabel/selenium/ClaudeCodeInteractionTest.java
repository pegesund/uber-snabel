package com.snabel.selenium;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ClaudeCodeInteractionTest.TestPortProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClaudeCodeInteractionTest {

    public static class TestPortProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.port", "8082");
        }
    }

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String sessionId;
    private static final int TEST_PORT = 8082;

    @BeforeAll
    public static void setupDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
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
        System.out.println("Step 1: Creating session...");

        // Navigate to the application
        driver.get("http://localhost:" + TEST_PORT);

        // Wait for page to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("description")));

        // Fill in session details
        WebElement descriptionField = driver.findElement(By.id("description"));
        descriptionField.clear();
        descriptionField.sendKeys("Selenium Test Session");

        WebElement instructionsField = driver.findElement(By.id("instructions"));
        instructionsField.clear();
        instructionsField.sendKeys("You are in a test session. Please respond to user messages.");

        // Click create session button
        WebElement createButton = driver.findElement(By.xpath("//button[contains(text(), 'Create Session')]"));
        createButton.click();

        // Wait for alert and capture session ID
        wait.until(ExpectedConditions.alertIsPresent());
        Alert alert = driver.switchTo().alert();
        String alertText = alert.getText();
        System.out.println("Alert text: " + alertText);

        // Extract session ID from alert
        assertTrue(alertText.contains("Session created:"), "Alert should contain 'Session created:'");
        assertFalse(alertText.contains("undefined"), "Session ID should not be undefined");

        // Extract the actual session ID (format: "Session created: <uuid>")
        String[] parts = alertText.split("Session created:");
        if (parts.length > 1) {
            sessionId = parts[1].trim().split("\\n")[0].trim();
            System.out.println("Extracted session ID: " + sessionId);
        }

        alert.accept();

        // Verify upload step is visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("step-upload")));

        System.out.println("✓ Session created successfully");
    }

    @Test
    @Order(2)
    public void testStartWithoutUpload() {
        System.out.println("Step 2: Starting Claude Code without upload...");

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
        System.out.println("Start alert: " + alertText);

        assertTrue(alertText.contains("Claude Code started"), "Should show Claude Code started message");
        alert.accept();

        // Wait for monitor step to be visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("step-monitor")));

        // Wait for log output to appear
        WebElement logOutput = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.id("log-output"))
        );

        System.out.println("✓ Claude Code start initiated");

        // Wait a bit for Claude to actually start
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Order(3)
    public void testSendCommandAndVerifyResponse() {
        System.out.println("Step 3: Sending 'say hello to me' command...");

        // Find command input field
        WebElement commandInput = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.id("command-input"))
        );

        // Type the command
        commandInput.clear();
        commandInput.sendKeys("say hello to me");

        // Click send button or press Enter
        commandInput.sendKeys(Keys.ENTER);

        System.out.println("✓ Command sent");

        // Wait for response in log output
        WebElement logOutput = driver.findElement(By.id("log-output"));

        // Wait up to 30 seconds for Claude to respond
        wait.until(driver -> {
            String logText = logOutput.getText().toLowerCase();
            System.out.println("Log content: " + logText);

            // Check if Claude responded with "hello" or similar greeting
            return logText.contains("hello") ||
                   logText.contains("hi") ||
                   logText.contains("greet");
        });

        String finalLog = logOutput.getText();
        System.out.println("Final log output:\n" + finalLog);

        // Verify the response contains a greeting
        String logLower = finalLog.toLowerCase();
        assertTrue(
            logLower.contains("hello") || logLower.contains("hi") || logLower.contains("greet"),
            "Claude should respond with a greeting"
        );

        System.out.println("✓ Claude responded successfully");
    }

    @Test
    @Order(4)
    public void testVerifyClaudeIsResponsive() {
        System.out.println("Step 4: Verifying Claude is still responsive...");

        WebElement commandInput = driver.findElement(By.id("command-input"));
        commandInput.clear();
        commandInput.sendKeys("What is 2+2?");
        commandInput.sendKeys(Keys.ENTER);

        // Wait for response mentioning "4" or "four"
        WebElement logOutput = driver.findElement(By.id("log-output"));

        wait.until(driver -> {
            String logText = logOutput.getText().toLowerCase();
            return logText.contains("4") || logText.contains("four");
        });

        System.out.println("✓ Claude is responsive and answering questions");
    }
}
