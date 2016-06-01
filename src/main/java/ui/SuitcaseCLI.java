package ui;

import model.AggregateInfo;
import model.CsvConfig;
import model.ODKCsv;
import net.AttachmentManager;
import net.DownloadTask;
import net.ResetTask;
import net.UploadTask;
import org.apache.commons.cli.*;
import org.apache.wink.json4j.JSONException;
import utils.FieldsValidatorUtils;
import utils.FileUtils;

import java.net.MalformedURLException;

import static ui.MessageString.*;

public class SuitcaseCLI {
  private enum Operation {
    DOWNLOAD, UPLOAD, RESET, INFO
  }

  private String[] args;

  private Options cliOptions;
  private AggregateInfo aggInfo;

  private String tableId;
  private String version;
  private String path;
  private boolean downloadAttachment;
  private boolean scanFormatting;
  private boolean extraMetadata;
  private boolean force;

  public SuitcaseCLI(String[] args) {
    this.args = args;

    this.cliOptions = buildOptions();
  }

  public void startCLI() {
    Operation operation = parseArgs(args, cliOptions);

    if (operation == null) {
      // this means some error was found when parsing arguments
      return;
    }

    String error;

    switch (operation) {
    case DOWNLOAD:
      AttachmentManager attMngr = new AttachmentManager(aggInfo, tableId, path);
      ODKCsv csv = null;
      try {
        csv = new ODKCsv(attMngr, aggInfo, tableId);
      } catch (JSONException e) { /* should never happen */ }
      CsvConfig config = new CsvConfig(downloadAttachment, scanFormatting, extraMetadata);

      error = FieldsValidatorUtils.checkDownloadFields(tableId, path, aggInfo);
      if (error != null) {
        DialogUtils.showError(error, false);
      } else {
        new DownloadTask(aggInfo, csv, config,
            path == null ? FileUtils.getDefaultSavePath().toString() : path, false).execute();
      }
      break;
    case UPLOAD:
      error = FieldsValidatorUtils.checkUploadFields(version, path);

      if (error != null) {
        DialogUtils.showError(error, false);
      } else {
        new UploadTask(aggInfo, path == null ? FileUtils.getDefaultUploadPath().toString() : path,
            version, false).execute();
      }
      break;
    case RESET:
      error = FieldsValidatorUtils.checkResetFields(version);

      if (error != null) {
        DialogUtils.showError(error, false);
      } else {
        new ResetTask(version, false).execute();
      }
      break;
    }
  }

  private Options buildOptions() {
    Options opt = new Options();

    //operations
    OptionGroup operation = new OptionGroup();
    operation.addOption(new Option("download", false, "Download csv"));
    operation.addOption(new Option("upload", false, "Upload csv"));
    operation.addOption(new Option("reset", false, "Reset server"));
    operation.setRequired(true);
    opt.addOptionGroup(operation);

    //aggregate related
    Option aggUrl = new Option("aggregate-url", true, "url to Aggregate server");
    aggUrl.setRequired(true);
    opt.addOption(aggUrl);

    Option appId = new Option("app-id", true, "app id");
    appId.setRequired(true);
    opt.addOption(appId);

    Option tableId = new Option("table-id", true, "table id");
    tableId.setRequired(true);
    opt.addOption(tableId);

    opt.addOption("username", true, "username"); // not required
    opt.addOption("password", true, "password"); // not required

    // not required for download, check later
    opt.addOption("data-version", true, "version of data, usually 1 or 2");

    //csv options
    opt.addOption("a", "attachment", false, "download attachments");
    opt.addOption("s", "scan", false, "apply Scan formatting");
    opt.addOption("e", "extra", false, "add extra metadata columns");

    opt.addOption("path", true, "Specify a custom path to output csv or to upload from. "
                              + "Default csv directory is ./Download/ "
                              + "Default upload directory is ./Upload/ ");

    //UI
    opt.addOption("f", "force", false, "do not prompt, overwrite existing files");

    //misc
    opt.addOption("h", "help", false, "print this message");
    opt.addOption("v", "version", false, "prints version information");

    return opt;
  }

  /**
   * Parses user arguments from pre-specified Options.
   *
   * @param args    Arguments passed to by user
   * @param options Options to parse from
   * @return false when either "-h" or "-v" is passed, otherwise true
   */
  private Operation parseArgs(String[] args, Options options) {
    Operation operation = null;

    try {
      CommandLineParser parser = new DefaultParser();
      CommandLine line = parser.parse(options, args);

      //handle -h and --help
      if (line.hasOption('h')) {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("suitcase", options);
        return Operation.INFO;
      }

      //handle -v
      if (line.hasOption('v')) {
        System.out.println("ODK Suitcase 2.0");
        return Operation.INFO;
      }

      if (line.hasOption("upload")) {
        operation = Operation.UPLOAD;
      } else if (line.hasOption("reset")) {
        operation = Operation.RESET;
      } else {
        operation = Operation.DOWNLOAD;
      }

      if (operation != Operation.DOWNLOAD && !line.hasOption("data-version")) {
        throw new ParseException("Data version is required for upload and reset");
      }

      //Aggregate related
      String username = line.getOptionValue("username");
      String password = line.getOptionValue("password");

      // validate fields before creating AggregateInfo object
      String error = FieldsValidatorUtils.checkLoginFields(
          line.getOptionValue("aggregate_url"), line.getOptionValue("app_id"),
          username, password, username.isEmpty() && password.isEmpty()
      );
      if (error != null) {
        DialogUtils.showError(error, false);
        // return early when validation fails
        return null;
      }
      aggInfo = new AggregateInfo(
          line.getOptionValue("aggregate_url"), line.getOptionValue("app_id"),
          line.getOptionValue("username"), line.getOptionValue("password")
      );
      tableId = line.getOptionValue("table_id");

      if (operation == Operation.DOWNLOAD) {
        //CSV options
        downloadAttachment = line.hasOption("a");
        scanFormatting = line.hasOption("s");
        extraMetadata = line.hasOption("e");
      }

      path = line.getOptionValue("path");
      version = line.getOptionValue("data-version");

      force = line.hasOption("f");
    } catch (ParseException e) {
      e.printStackTrace();
    } catch (MalformedURLException e) {
      DialogUtils.showError(BAD_URL, false);
      e.printStackTrace();
    }

    return operation;
  }
}