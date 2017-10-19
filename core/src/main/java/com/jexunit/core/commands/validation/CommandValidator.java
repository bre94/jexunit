package com.jexunit.core.commands.validation;

import com.jexunit.core.JExUnitConfig;
import com.jexunit.core.commands.TestCommandScanner;
import com.jexunit.core.model.TestCase;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.fail;

public class CommandValidator {

    private static Logger log = Logger.getLogger(CommandValidator.class.getName());

    /**
     * Validates test cases after they are parsed
     *
     * @param testData test data (loaded by a data provider)
     */
    public static void validateCommands(Collection<Object[]> testData) {
        ValidationType validationType = ValidationType.valueOf(
                JExUnitConfig.getStringProperty(JExUnitConfig.ConfigKey.COMMAND_VALIDATION_TYPE));
        if (validationType == ValidationType.IGNORE) {
            return;
        }
        for (Object[] objects : testData) {
            List<TestCase> testCases = (List<TestCase>) objects[0];
            Iterator<TestCase> iterator = testCases.iterator();
            while (iterator.hasNext()) {
                TestCase testCase = iterator.next();
                if (!TestCommandScanner.isTestCommandValid(testCase.getTestCommand().toLowerCase())) {
                    if (validationType == ValidationType.WARN) {
                        log.log(Level.WARNING, "TestCommand {0} is not valid. TestCase will be removed!",
                                testCase.getTestCommand());
                        iterator.remove();
                    } else if (validationType == ValidationType.FAIL) {
                        fail(String.format("TestCommand %s is not valid.",
                                testCase.getTestCommand()));
                    }
                }
            }
        }
    }
}
