package eu.sollers;

import com.opencsv.CSVWriter;
import org.ini4j.Wini;
import org.ini4j.Config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App {

    private static final String scriptName = "PolicyIssueClean";

    private static final String pathToScriptDir = "C:\\Users\\hubert.rosiak\\Documents\\VuGen\\Scripts\\" + scriptName + "\\";
    private static final String scriptActionFilename = "Action.c";
    private static final String scriptActionBackupFilename = "Action.bak";
    private static final String prmFilename = scriptName + ".prm";
    private static final String usrFilename = scriptName + ".usr";
    private static final String datFilename = "params.dat";

    private static final String pathToWorkingDir = "C:\\Users\\hubert.rosiak\\Desktop\\Script Processing\\";
    private static final String keywordsFoundFilename = "keywordsFound.csv";


    private static void extractKeywords(String[] keywords) throws IOException {

        String[] lines = Files.readAllLines(Paths.get(pathToScriptDir, scriptActionFilename)).toArray(new String[0]);

        String regex_header = "Name\\s?=(.*)\",\\s?\"Value\\s?=\\s?(%s)\\s?\"";
        String regex_body = "%%3A((?:(?!%%3A).)*)=(%s)[&\"]";  // "^.*%%3A(.*)=(%s)[&\"]";

        try (Writer writer = Files.newBufferedWriter(Paths.get(pathToWorkingDir, keywordsFoundFilename));
             CSVWriter csvWriter = new CSVWriter(writer,
                     CSVWriter.DEFAULT_SEPARATOR,
                     CSVWriter.NO_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                     CSVWriter.DEFAULT_LINE_END)) {

            for (String keyword : keywords) {
                Pattern pattern_header = Pattern.compile(String.format(regex_header, keyword), Pattern.CASE_INSENSITIVE);
                Pattern pattern_body = Pattern.compile(String.format(regex_body, keyword), Pattern.CASE_INSENSITIVE);

                for (int i = 0; i < lines.length; i++) {
                    Matcher matcher_header = pattern_header.matcher(lines[i]);
                    Matcher matcher_body = pattern_body.matcher(lines[i]);
                    while (matcher_header.find()) {
                        csvWriter.writeNext(new String[]{Integer.toString(i), matcher_header.group(1), matcher_header.group(2)});
                    }
                    while (matcher_body.find()) {
                        csvWriter.writeNext(new String[]{Integer.toString(i), matcher_body.group(1), matcher_body.group(2)});
                    }
                }
            }
        }
    }

    // replaces value for appropriate {param} in every line of the script
    private static void substituteParamsInScript(Map<String, int[]> params_indices) throws IOException {
        String[] script_lines = Files.readAllLines(Paths.get(pathToScriptDir, scriptActionFilename)).toArray(new String[0]);

        File actionFile = new File(pathToScriptDir + scriptActionFilename);
        File actionFileBackup = new File(pathToScriptDir + scriptActionBackupFilename);
        if (actionFileBackup.exists()) {
            throw new IOException(String.format("File %s exists", actionFileBackup));
        }
        if (!actionFile.renameTo(actionFileBackup)) {
            throw new IOException(String.format("Failed to rename %s", actionFile));
        }

        String regex_header = "(Name\\s?=(.*)\",\\s?\"Value\\s?=\\s?)(%s)(\\s?\")";
        String regex_body = "(%%3A((?:(?!%%3A).)*)=)(%s)([&\"])";  // "^.*%%3A(.*)=(%s)[&\"]";

        int keyword_number = 0;
        for (String keyword : params_indices.keySet()) {
            keyword_number++;

            Pattern pattern_header = Pattern.compile(String.format(regex_header, keyword), Pattern.CASE_INSENSITIVE);
            Pattern pattern_body = Pattern.compile(String.format(regex_body, keyword), Pattern.CASE_INSENSITIVE);

            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < script_lines.length; i++) {
                sb.setLength(0);
                Matcher matcher_header = pattern_header.matcher(script_lines[i]);
                while (matcher_header.find()) {
                    matcher_header.appendReplacement(sb, String.format("$1{param_%d}$4", keyword_number));
                }
                matcher_header.appendTail(sb);

                Matcher matcher_body = pattern_body.matcher(sb.toString());
                sb.setLength(0);
                while (matcher_body.find()) {
                    matcher_body.appendReplacement(sb, String.format("$1{param_%d}$4", keyword_number));
                }
                matcher_body.appendTail(sb);

                script_lines[i] = sb.toString();
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(pathToScriptDir + scriptActionFilename))) {
                for (String line : script_lines) {
                    writer.println(line);
                }
            }
        }
    }

    private static void createDatFiles(int number_of_params) throws IOException {
        File datFile = new File(pathToScriptDir + datFilename);

        try (Writer writer = new FileWriter(datFile, false);
             CSVWriter csvWriter = new CSVWriter(writer,
                     CSVWriter.DEFAULT_SEPARATOR,
                     CSVWriter.NO_QUOTE_CHARACTER,
                     CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                     CSVWriter.DEFAULT_LINE_END)) {

            String[] header = IntStream.range(1, number_of_params + 1).mapToObj(i -> String.format("param_%d", i)).toArray(String[]::new);
            csvWriter.writeNext(header);
        }
    }

    private static void createPrm(String[] paramsNames) throws IOException {
        File prmFile = new File(pathToScriptDir + prmFilename);

        if (!prmFile.delete()) {
            //throw new IOException(String.format("Cannot delete %s file", prmFile.toString()));
        }
        if (!prmFile.createNewFile()) {
            throw new IOException(String.format("Cannot create %s file", prmFile.toString()));
        }
        Wini prm = new Wini(prmFile);

        Config cPrm = prm.getConfig();
        cPrm.setStrictOperator(true);
        prm.setConfig(cPrm);

        for (int i = 0; i < paramsNames.length; i++) {
            int param_number = i + 1;
            String section = "parameter:param_" + param_number;
            prm.put(section, "ColumnName", String.format("\"Col %d\"", param_number));
            prm.put(section, "Delimiter", "\",\"");
            prm.put(section, "GenerateNewVal", "\"EachIteration\"");
            prm.put(section, "OriginalValue", String.format("\"%s\"", paramsNames[i]));
            prm.put(section, "OutOfRangePolicy", "\"ContinueWithLast\"");
            prm.put(section, "ParamName", String.format("\"param_%d\"", param_number));
            prm.put(section, "SelectNextRow", "\"Sequential\"");
            prm.put(section, "StartRow", "\"1\"");
            prm.put(section, "Table", "\"params.dat\"");
            prm.put(section, "TableLocation", "\"Local\"");
            prm.put(section, "Type", "\"Table\"");
            prm.put(section, "auto_allocate_block_size", "\"1\"");
            prm.put(section, "value_for_each_vuser", "\"\"");
        }
        prm.store();

        // delete empty lines from created .prm file
        File tempPrmFile = new File(pathToScriptDir + prmFilename + "_temp");
        if (tempPrmFile.exists()) {
            if (!tempPrmFile.delete()) {
                throw new IOException(String.format("Cannot delete %s file", tempPrmFile.toString()));
            }
        }

        try (Scanner reader = new Scanner(prmFile);
             PrintWriter writer = new PrintWriter(tempPrmFile)) {

            while (reader.hasNext()) {
                String line = reader.nextLine();
                line = line.trim();
                if (!line.isEmpty()) {
                    writer.println(line);
                }
            }
        }

        if (!prmFile.delete()) {
            throw new IOException(String.format("Cannot delete %s file", prmFile.toString()));
        }
        if (!tempPrmFile.renameTo(prmFile)) {
            throw new IOException(String.format("Failed to rename %s", tempPrmFile));
        }


        // updates .usr file to point to .prm file
        Wini usr = new Wini(new File(pathToScriptDir + usrFilename));

        Config cUsr = usr.getConfig();
        cUsr.setStrictOperator(true);
        usr.setConfig(cUsr);

        usr.put("General", "ParameterFile", scriptName + ".prm");
        usr.store();
    }

    public static void main(String[] args) {
        try {
            // get keywords from user that are candidates for substitution
            String[] keywords = {"Klemens", "Compo-Site"};

            // extract all keyword occurrences in script to a file for user to inspect
            extractKeywords(keywords);

            // get sets of line numbers (according to keywordsFound file) from user to substitute for params
            Map<String, int[]> params_indices = new HashMap<>();
            params_indices.put("Klemens", new int[]{130, 162, 192, 207, 222, 239, 314});
            params_indices.put("Compo-Site", new int[]{131, 163, 192, 207, 222, 239, 314});

            // substitute params in script
            substituteParamsInScript(params_indices);

            // create all necessary values in .ini file
            createPrm(params_indices.keySet().toArray(new String[0]));

            // create values for param in .dat file
            createDatFiles(params_indices.keySet().size());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
