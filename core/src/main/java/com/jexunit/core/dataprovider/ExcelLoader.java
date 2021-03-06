package com.jexunit.core.dataprovider;

import com.jexunit.core.JExUnitConfig;
import com.jexunit.core.commands.DefaultCommands;
import com.jexunit.core.model.TestCase;
import com.jexunit.core.model.TestCell;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility class for reading the excel file into the internal data representation.
 * <p>
 * TODO: add possibility to change the "test-direction" from horizontal to vertical?
 *
 * @author fabian
 */
public class ExcelLoader {

    // Utility class, only static access
    private ExcelLoader() {
    }

    /**
     * Load the excel-file and prepare the data (TestCommands). Each worksheet will run as separated Unit-Test.
     *
     * @param excelFile       the name of the excel file (to be loaded). It has to be the filename incl. path to be
     *                        loaded (for example: src/test/resources/myExcelFile.xls)
     * @param worksheetAsTest "group" all the test-commands of a worksheet to one test (true) or run each test-command
     *                        as single test (false)
     * @return a list of the parsed {@link TestCase}s
     * @throws Exception in case that something goes wrong
     */
    public static Collection<Object[]> loadTestData(final String excelFile, final boolean worksheetAsTest) throws Exception {
        final Map<String, List<TestCase<?>>> tests = readExcel(excelFile);

        final Collection<Object[]> col = new ArrayList<>();
        if (worksheetAsTest) {
            tests.forEach((k, v) -> col.add(new Object[]{v}));
        } else {
            tests.forEach((s, l) -> l.forEach(gtc -> {
                final List<TestCase<?>> list = new ArrayList<>();
                list.add(gtc);
                col.add(new Object[]{list});
            }));
        }

        return col;
    }

    /**
     * Read the excel-sheet and generate the GevoTestCases. Each worksheet will become its own list of GevoTestCases. So
     * each worksheet will run as separated testrun.
     *
     * @param excelFilePath the path to the excel-file to read
     * @return a map with the excel worksheet name as key and the list of {@link TestCase}s as value
     * @throws Exception in case that something goes wrong
     */
    static Map<String, List<TestCase<?>>> readExcel(final String excelFilePath) throws Exception {
        final Map<String, List<TestCase<?>>> tests = new LinkedHashMap<>();

        int i = 0;
        int j = 0;
        String sheet = null;
        try (final OPCPackage pkg = OPCPackage.open(excelFilePath, PackageAccess.READ);) {
            final XSSFWorkbook workbook = new XSSFWorkbook(pkg);
            // iterate through the worksheets
            for (final XSSFSheet worksheet : workbook) {
                sheet = worksheet.getSheetName();
                final List<TestCase<?>> testCases = new ArrayList<>();

                List<String> commandHeaders = null;

                // iterate through the rows
                for (i = 0; i <= worksheet.getLastRowNum(); i++) {
                    final XSSFRow row = worksheet.getRow(i);

                    if (row != null) {
                        final String cellValue = cellValues2String(workbook, row.getCell(0));
                        if (JExUnitConfig.getStringProperty(JExUnitConfig.ConfigKey.COMMAND_STATEMENT)
                                .equalsIgnoreCase(cellValue)) {
                            commandHeaders = new ArrayList<>();

                            // iterate through the columns
                            for (int h = 0; h < row.getLastCellNum(); h++) {
                                commandHeaders.add(row.getCell(h).getStringCellValue());
                            }
                        } else {
                            if (cellValue == null || cellValue.isEmpty()) {
                                // if the first column is empty, this is a comment line and will be
                                // ignored
                                continue;
                            } else if (JExUnitConfig.getDefaultCommandProperty(DefaultCommands.DISABLED)
                                    .equalsIgnoreCase(cellValue)) {
                                final TestCase<ExcelMetadata> testCase = new TestCase<>(new ExcelMetadata());

                                // the first column is always the command
                                testCase.setTestCommand(cellValue);
                                testCase.getMetadata().setSheet(worksheet.getSheetName());
                                testCase.getMetadata().setRow(row.getRowNum() + 1);

                                if (row.getLastCellNum() >= 1) {
                                    final TestCell testCell = new TestCell();
                                    testCell.setvalue(cellValues2String(workbook, row.getCell(1)));
                                    testCell.setColumn(row.getCell(1).getColumnIndex() + 1);
                                    testCase.getValues().put(
                                            JExUnitConfig.getDefaultCommandProperty(DefaultCommands.DISABLED),
                                            testCell);
                                    testCase.setDisabled(Boolean.parseBoolean(testCell.getValue()));
                                }
                                testCases.add(testCase);
                            } else if (commandHeaders != null || JExUnitConfig
                                    .getDefaultCommandProperty(DefaultCommands.REPORT).equalsIgnoreCase(cellValue)) {
                                final TestCase<ExcelMetadata> testCase = new TestCase<>(new ExcelMetadata());

                                // the first column is always the command
                                testCase.setTestCommand(cellValue);
                                testCase.getMetadata().setSheet(worksheet.getSheetName());
                                testCase.getMetadata().setRow(row.getRowNum() + 1);

                                for (j = 1; j < row.getLastCellNum(); j++) {
                                    final TestCell testCell = new TestCell();
                                    testCell.setvalue(cellValues2String(workbook, row.getCell(j)));
                                    testCell.setColumn(row.getCell(j).getColumnIndex() + 1);
                                    // the "report"-command doesn't need a header-line
                                    testCase.getValues().put(commandHeaders != null && commandHeaders.size() > j
                                            ? commandHeaders.get(j) : "param" + j, testCell);

                                    // read/parse the "default" commands/parameters
                                    if (commandHeaders != null && commandHeaders.size() > j) {
                                        final String header = commandHeaders.get(j);
                                        if (JExUnitConfig.getDefaultCommandProperty(DefaultCommands.BREAKPOINT)
                                                .equalsIgnoreCase(commandHeaders.get(j))) {
                                            // each command has the ability to set a breakpoint to
                                            // debug the test more easily
                                            testCase.setBreakpointEnabled(Boolean.parseBoolean(testCell.getValue()));
                                        } else if (JExUnitConfig
                                                .getDefaultCommandProperty(DefaultCommands.EXCEPTION_EXCPECTED)
                                                .equalsIgnoreCase(header)) {
                                            // each command has the ability to expect an exception.
                                            // you can define this via the field EXCEPTION_EXPECTED.
                                            testCase.setExceptionExpected(Boolean.parseBoolean(testCell.getValue()));
                                        } else if (JExUnitConfig.getDefaultCommandProperty(DefaultCommands.DISABLED)
                                                .equalsIgnoreCase(header)) {
                                            // each command can be disabled
                                            testCase.setDisabled(Boolean.parseBoolean(testCell.getValue()));
                                        } else if (JExUnitConfig.getDefaultCommandProperty(DefaultCommands.COMMENT)
                                                .equalsIgnoreCase(header)) {
                                            // add the comment to the test-case
                                            testCase.setComment(testCell.getValue());
                                        } else if (JExUnitConfig.getDefaultCommandProperty(DefaultCommands.FAST_FAIL)
                                                .equalsIgnoreCase(header)) {
                                            // the command can fast fail the complete test sheet on fail
                                            testCase.setFastFail(Boolean.parseBoolean(testCell.getValue()));
                                        }
                                    }
                                }
                                testCases.add(testCase);
                            }
                        }
                    }
                }

                tests.put(worksheet.getSheetName(), testCases);
            }
        } catch (final FileNotFoundException e) {
            throw new Exception(String.format("Excel-file '%s' not found!", excelFilePath), e);
        } catch (final Exception e) {
            throw new Exception(String.format("Error while reading the excel-file! - worksheet: %s row: %s column: %s",
                    sheet, i + 1, getColumn(j + 1)), e);
        }
        return tests;
    }

    /**
     * Get the value of the excel-cell as String.
     *
     * @param workbook workbook (excel) for evaluating cell formulas
     * @param cell     cell (excel)
     * @return the value of the excel-cell as String
     */
    static String cellValues2String(final XSSFWorkbook workbook, final XSSFCell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case XSSFCell.CELL_TYPE_NUMERIC:
                if (HSSFDateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat(JExUnitConfig.getStringProperty(JExUnitConfig.ConfigKey.DATE_PATTERN))
                            .format(cell.getDateCellValue());
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case XSSFCell.CELL_TYPE_STRING:
                return cell.getStringCellValue();
            case XSSFCell.CELL_TYPE_FORMULA:
                return evaluateCellFormula(workbook, cell);
            case XSSFCell.CELL_TYPE_BLANK:
                return cell.getStringCellValue();
            case XSSFCell.CELL_TYPE_BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case XSSFCell.CELL_TYPE_ERROR:
                return String.valueOf(cell.getErrorCellValue());
        }
        return null;
    }

    /**
     * Evaluate the formula of the given cell.
     *
     * @param workbook workbook (excel) for evaluating the cell formula
     * @param cell     cell (excel)
     * @return the value of the excel-call as string (the formula will be executed)
     */
    static String evaluateCellFormula(final XSSFWorkbook workbook, final XSSFCell cell) {
        final FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        final CellValue cellValue = evaluator.evaluate(cell);

        switch (cellValue.getCellType()) {
            case Cell.CELL_TYPE_BOOLEAN:
                return String.valueOf(cellValue.getBooleanValue());
            case Cell.CELL_TYPE_NUMERIC:
                return String.valueOf(cellValue.getNumberValue());
            case Cell.CELL_TYPE_STRING:
                return cellValue.getStringValue();
            default:
                return null;
        }
    }

    /**
     * Get the character(s) of the column like it is in excel (A, B, C, ...)
     *
     * @param column the column index
     * @return the name of the column (A, B, C, ...)
     */
    public static String getColumn(int column) {
        column--;
        if (column >= 0 && column < 26) {
            return Character.toString((char) ('A' + column));
        } else if (column > 25) {
            return getColumn(column / 26) + getColumn(column % 26 + 1);
        } else {
            throw new IllegalArgumentException("Invalid Column #" + Character.toString((char) (column + 1)));
        }
    }

}
