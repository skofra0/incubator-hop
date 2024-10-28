/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.mail;

import jakarta.activation.DataHandler;
import jakarta.activation.URLDataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelectInfo;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.Const;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/** Send mail transform. based on Mail action */
public class Mail extends BaseTransform<MailMeta, MailData> {

  private static final Class<?> PKG = MailMeta.class;
  public static final String CONST_MAIL = "mail.";

  public Mail(
      TransformMeta transformMeta,
      MailMeta meta,
      MailData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {

    Object[] r = getRow(); // get row, set busy!
    if (r == null) { // no more input to be expected...

      setOutputDone();
      return false;
    }

    if (first) {
      first = false;

      // Making sure the mail API finds the right resources
      //
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

      // get the RowMeta
      data.previousRowMeta = getInputRowMeta().clone();

      if (meta.isAddMessageToOutput()) {
        if (Utils.isEmpty(variables.resolve(meta.getMessageOutputField()))) {
          throw new HopException(BaseMessages.getString(PKG, "Mail.Log.OutputFieldEmpty"));
        }
        data.previousRowMeta.addValueMeta(
            new ValueMetaString(variables.resolve(meta.getMessageOutputField())));
      }

      // Check is filename field is provided
      if (Utils.isEmpty(meta.getDestination())) {
        throw new HopException(BaseMessages.getString(PKG, "Mail.Log.DestinationFieldEmpty"));
      }

      // Check is replyname field is provided
      if (Utils.isEmpty(meta.getReplyAddress())) {
        throw new HopException(BaseMessages.getString(PKG, "Mail.Log.ReplyFieldEmpty"));
      }

      // Check is SMTP server is provided
      if (Utils.isEmpty(meta.getServer()) && !meta.isUsingConfigFile()) { // DEEM-MOD
        throw new HopException(BaseMessages.getString(PKG, "Mail.Log.ServerFieldEmpty"));
      }

      // Check Attached filenames when dynamic
      if (meta.isDynamicFilename() && Utils.isEmpty(meta.getDynamicFieldname())) {
        throw new HopException(BaseMessages.getString(PKG, "Mail.Log.DynamicFilenameFielddEmpty"));
      }

      // Check Attached zipfilename when dynamic
      if (meta.isZipFilenameDynamic() && Utils.isEmpty(meta.getDynamicZipFilenameField())) {
        throw new HopException(
            BaseMessages.getString(PKG, "Mail.Log.DynamicZipFilenameFieldEmpty"));
      }

      if (meta.isZipFiles() && Utils.isEmpty(meta.getZipFilename())) {
        throw new HopException(BaseMessages.getString(PKG, "Mail.Log.ZipFilenameEmpty"));
      }

      // check authentication
      if (meta.isUsingAuthentication()) {
        // check authentication user
        if (Utils.isEmpty(meta.getAuthenticationUser())) {
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Log.AuthenticationUserFieldEmpty"));
        }

        // check authentication pass
        if (Utils.isEmpty(meta.getAuthenticationPassword())) {
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Log.AuthenticationPasswordFieldEmpty"));
        }
      }

      // cache the position of the destination field
      if (data.indexOfDestination < 0) {
        String realDestinationFieldname = meta.getDestination();
        data.indexOfDestination = data.previousRowMeta.indexOfValue(realDestinationFieldname);
        if (data.indexOfDestination < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindDestinationField", realDestinationFieldname));
        }
      }

      // Cc
      if (!Utils.isEmpty(meta.getDestinationCc()) && data.indexOfDestinationCc < 0) {
        // cache the position of the Cc field
        String realDestinationCcFieldname = meta.getDestinationCc();
        data.indexOfDestinationCc = data.previousRowMeta.indexOfValue(realDestinationCcFieldname);
        if (data.indexOfDestinationCc < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG,
                  "Mail.Exception.CouldnotFindDestinationCcField",
                  realDestinationCcFieldname));
        }
      }
      // BCc
      if (!Utils.isEmpty(meta.getDestinationBCc()) && data.indexOfDestinationBCc < 0) {
        // cache the position of the BCc field
        String realDestinationBCcFieldname = meta.getDestinationBCc();
        data.indexOfDestinationBCc = data.previousRowMeta.indexOfValue(realDestinationBCcFieldname);
        if (data.indexOfDestinationBCc < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG,
                  "Mail.Exception.CouldnotFindDestinationBCcField",
                  realDestinationBCcFieldname));
        }
      }
      // Sender Name
      if (!Utils.isEmpty(meta.getReplyName()) && data.indexOfSenderName < 0) {
        // cache the position of the sender field
        String realSenderName = meta.getReplyName();
        data.indexOfSenderName = data.previousRowMeta.indexOfValue(realSenderName);
        if (data.indexOfSenderName < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindReplyNameField", realSenderName));
        }
      }
      // Sender address
      // cache the position of the sender field
      if (data.indexOfSenderAddress < 0) {
        String realSenderAddress = meta.getReplyAddress();
        data.indexOfSenderAddress = data.previousRowMeta.indexOfValue(realSenderAddress);
        if (data.indexOfSenderAddress < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindReplyAddressField", realSenderAddress));
        }
      }

      // Reply to
      if (!Utils.isEmpty(meta.getReplyToAddresses()) && data.indexOfReplyToAddresses < 0) {
        // cache the position of the reply to field

        String realReplyToAddresses = meta.getReplyToAddresses();
        data.indexOfReplyToAddresses = data.previousRowMeta.indexOfValue(realReplyToAddresses);
        if (data.indexOfReplyToAddresses < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindReplyToAddressesField", realReplyToAddresses));
        }
      }

      // Contact Person
      if (!Utils.isEmpty(meta.getContactPerson()) && data.indexOfContactPerson < 0) {
        // cache the position of the destination field

        String realContactPerson = meta.getContactPerson();
        data.indexOfContactPerson = data.previousRowMeta.indexOfValue(realContactPerson);
        if (data.indexOfContactPerson < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindContactPersonField", realContactPerson));
        }
      }
      // Contact Phone
      if (!Utils.isEmpty(meta.getContactPhone()) && data.indexOfContactPhone < 0) {
        // cache the position of the destination field

        String realContactPhone = meta.getContactPhone();
        data.indexOfContactPhone = data.previousRowMeta.indexOfValue(realContactPhone);
        if (data.indexOfContactPhone < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindContactPhoneField", realContactPhone));
        }
      }
      // cache the position of the Server field
      if (data.indexOfServer < 0) {
        String realServer = meta.getServer();
        data.indexOfServer = data.previousRowMeta.indexOfValue(realServer);
        if (data.indexOfServer < 0 && !meta.isUsingConfigFile()) { // DEEM-MOD
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.CouldnotFindServerField", realServer));
        }
      }
      // Port
      if (!Utils.isEmpty(meta.getPort()) && data.indexOfPort < 0) {
        // cache the position of the port field

        String realPort = meta.getPort();
        data.indexOfPort = data.previousRowMeta.indexOfValue(realPort);
        if (data.indexOfPort < 0 && !meta.isUsingConfigFile()) { // DEEM-MOD
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.CouldnotFindPortField", realPort));
        }
      }
      // Authentication
      if (meta.isUsingAuthentication()) {
        // cache the position of the Authentication user field
        if (data.indexOfAuthenticationUser < 0) {
          String realAuthenticationUser = meta.getAuthenticationUser();
          data.indexOfAuthenticationUser =
              data.previousRowMeta.indexOfValue(realAuthenticationUser);
          if (data.indexOfAuthenticationUser < 0) {
            throw new HopException(
                BaseMessages.getString(
                    PKG,
                    "Mail.Exception.CouldnotFindAuthenticationUserField",
                    realAuthenticationUser));
          }
        }

        // cache the position of the Authentication password field
        if (data.indexOfAuthenticationPass < 0) {
          String realAuthenticationPassword = meta.getAuthenticationPassword();
          data.indexOfAuthenticationPass =
              data.previousRowMeta.indexOfValue(realAuthenticationPassword);
          if (data.indexOfAuthenticationPass < 0) {
            throw new HopException(
                BaseMessages.getString(
                    PKG,
                    "Mail.Exception.CouldnotFindAuthenticationPassField",
                    realAuthenticationPassword));
          }
        }
      }
      // Mail Subject
      if (!Utils.isEmpty(meta.getSubject()) && data.indexOfSubject < 0) {
        // cache the position of the subject field
        String realSubject = meta.getSubject();
        data.indexOfSubject = data.previousRowMeta.indexOfValue(realSubject);
        if (data.indexOfSubject < 0) {
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.CouldnotFindSubjectField", realSubject));
        }
      }
      // Mail Comment
      if (!Utils.isEmpty(meta.getComment()) && data.indexOfComment < 0) {
        // cache the position of the comment field
        String realComment = meta.getComment();
        data.indexOfComment = data.previousRowMeta.indexOfValue(realComment);
        if (data.indexOfComment < 0) {
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.CouldnotFindCommentField", realComment));
        }
      }

      if (meta.isAttachContentFromField()) {
        // We are dealing with file content directly loaded from file
        // and not physical file
        String attachedContentField = meta.getAttachContentField();
        if (Utils.isEmpty(attachedContentField)) {
          // Empty Field
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.AttachedContentFieldEmpty"));
        }
        data.indexOfAttachedContent = data.previousRowMeta.indexOfValue(attachedContentField);
        if (data.indexOfAttachedContent < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG, "Mail.Exception.CouldnotFindAttachedContentField", attachedContentField));
        }
        // Attached content filename
        String attachedContentFileNameField = meta.getAttachContentFileNameField();
        if (Utils.isEmpty(attachedContentFileNameField)) {
          // Empty Field
          throw new HopException(
              BaseMessages.getString(PKG, "Mail.Exception.AttachedContentFileNameFieldEmpty"));
        }
        data.indexOfAttachedFilename =
            data.previousRowMeta.indexOfValue(attachedContentFileNameField);
        if (data.indexOfAttachedFilename < 0) {
          throw new HopException(
              BaseMessages.getString(
                  PKG,
                  "Mail.Exception.CouldnotFindAttachedContentFileNameField",
                  attachedContentFileNameField));
        }

      } else {

        // Dynamic Zipfilename
        if (meta.isZipFilenameDynamic() && data.indexOfDynamicZipFilename < 0) {
          // cache the position of the attached source filename field
          String realZipFilename = meta.getDynamicZipFilenameField();
          data.indexOfDynamicZipFilename = data.previousRowMeta.indexOfValue(realZipFilename);
          if (data.indexOfDynamicZipFilename < 0) {
            throw new HopException(
                BaseMessages.getString(
                    PKG, "Mail.Exception.CouldnotSourceAttachedZipFilenameField", realZipFilename));
          }
        }
        data.zipFileLimit = Const.toLong(resolve(meta.getZipLimitSize()), 0);
        if (data.zipFileLimit > 0) {
          data.zipFileLimit = data.zipFileLimit * 1048576; // Mo
        }

        if (!meta.isZipFilenameDynamic()) {
          data.ZipFilename = resolve(meta.getZipFilename());
        }

        // Attached files
        if (meta.isDynamicFilename()) {
          // cache the position of the attached source filename field
          if (data.indexOfSourceFilename < 0) {
            String realSourceattachedFilename = meta.getDynamicFieldname();
            data.indexOfSourceFilename =
                data.previousRowMeta.indexOfValue(realSourceattachedFilename);
            if (data.indexOfSourceFilename < 0) {
              throw new HopException(
                  BaseMessages.getString(
                      PKG,
                      "Mail.Exception.CouldnotSourceAttachedFilenameField",
                      realSourceattachedFilename));
            }
          }

          // cache the position of the attached wildcard field
          if (!Utils.isEmpty(meta.getSourceWildcard()) && data.indexOfSourceWildcard < 0) {
            String realSourceattachedWildcard = meta.getDynamicWildcard();
            data.indexOfSourceWildcard =
                data.previousRowMeta.indexOfValue(realSourceattachedWildcard);
            if (data.indexOfSourceWildcard < 0) {
              throw new HopException(
                  BaseMessages.getString(
                      PKG,
                      "Mail.Exception.CouldnotSourceAttachedWildcard",
                      realSourceattachedWildcard));
            }
          }
        } else {
          // static attached filenames
          data.realSourceFileFoldername = resolve(meta.getSourceFileFoldername());
          data.realSourceWildcard = resolve(meta.getSourceWildcard());
        }
      }

      // check embedded images
      if (meta.getEmbeddedImages() != null && meta.getEmbeddedImages().length > 0) {
        FileObject image = null;
        data.embeddedMimePart = new HashSet<>();
        try {
          for (int i = 0; i < meta.getEmbeddedImages().length; i++) {
            String imageFile = resolve(meta.getEmbeddedImages()[i]);
            String contentID = resolve(meta.getContentIds()[i]);
            image = HopVfs.getFileObject(imageFile, variables);

            if (image.exists() && image.getType() == FileType.FILE) {
              // Create part for the image
              MimeBodyPart imagePart = new MimeBodyPart();
              // Load the image
              URLDataSource fds = new URLDataSource(image.getURL());
              imagePart.setDataHandler(new DataHandler(fds));
              // Setting the header
              imagePart.setHeader("Content-ID", "<" + contentID + ">");
              // keep this part for further user
              data.embeddedMimePart.add(imagePart);
              logBasic(BaseMessages.getString(PKG, "Mail.Log.ImageAdded", imageFile));

            } else {
              logError(BaseMessages.getString(PKG, "Mail.Log.WrongImage", imageFile));
            }
          }
        } catch (Exception e) {
          logError(BaseMessages.getString(PKG, "Mail.Error.AddingImage", e.getMessage()));
        } finally {
          if (image != null) {
            try {
              image.close();
            } catch (Exception e) {
              /* Ignore */
            }
          }
        }
      }
      data.usingAuthentication = meta.isUsingAuthentication(); // DEEM-MOD
      data.usingConfig = meta.isUsingConfigFile(); // DEEM-MOD
    } // end if first

    boolean sendToErrorRow = false;
    String errorMessage = null;

    try {
      // DEEM-MOD START
      if (meta.isUsingConfigFile()) {
        if (data.props.isEmpty()) {
          String path = resolve(meta.getConfigFile());
          InputStream inStream = Files.newInputStream(Paths.get(path));
          data.props.load(inStream);
          if (!Utils.toStringNullToEmpty(data.props.get("mail.smtps.host")).isEmpty()) {
            data.protocol = "smtps";
          }
          data.server =
              Utils.toStringNullToEmpty(data.props.get("mail." + data.protocol + ".host"));
          data.authenticationUser =
              Utils.toStringNullToEmpty(data.props.get("mail." + data.protocol + ".user"));
          data.authenticationPassword =
              Utils.toStringNullToEmpty(data.props.get("mail." + data.protocol + ".password"));
          data.usingAuthentication =
              Utils.toStringNullToEmpty(data.props.get("mail." + data.protocol + ".auth"))
                  .equalsIgnoreCase("true");

          String strPort =
              Utils.toStringNullToEmpty(data.props.get("mail." + data.protocol + ".port"));
          if (!strPort.isEmpty()) {
            data.port = Integer.parseInt(strPort);
          }
        }
      } else {
        data.usingAuthentication = meta.isUsingAuthentication();
      }
      // DEEM-MOD END

      // get values
      String maildestination = data.previousRowMeta.getString(r, data.indexOfDestination);
      if (Utils.isEmpty(maildestination)) {
        throw new HopException("Mail.Error.MailDestinationEmpty");
      }
      String maildestinationCc = null;
      if (data.indexOfDestinationCc > -1) {
        maildestinationCc = data.previousRowMeta.getString(r, data.indexOfDestinationCc);
      }
      String maildestinationBCc = null;
      if (data.indexOfDestinationBCc > -1) {
        maildestinationBCc = data.previousRowMeta.getString(r, data.indexOfDestinationBCc);
      }

      String mailsendername = null;
      if (data.indexOfSenderName > -1) {
        mailsendername = data.previousRowMeta.getString(r, data.indexOfSenderName);
      }
      String mailsenderaddress = data.previousRowMeta.getString(r, data.indexOfSenderAddress);

      // reply addresses
      String mailreplyToAddresses = null;
      if (data.indexOfReplyToAddresses > -1) {
        mailreplyToAddresses = data.previousRowMeta.getString(r, data.indexOfReplyToAddresses);
      }

      String contactperson = null;
      if (data.indexOfContactPerson > -1) {
        contactperson = data.previousRowMeta.getString(r, data.indexOfContactPerson);
      }
      String contactphone = null;
      if (data.indexOfContactPhone > -1) {
        contactphone = data.previousRowMeta.getString(r, data.indexOfContactPhone);
      }

      if (!meta.isUsingConfigFile()) { // DEEM-MOD
        data.server = data.previousRowMeta.getString(r, data.indexOfServer);
      }
      if (Utils.isEmpty(data.server)) { // DEEM-MOD
        throw new HopException("Mail.Error.MailServerEmpty");
      }
      // int port = -1; // DEEM-MOD
      if (data.indexOfPort > -1) {
        data.port =
            Const.toInt("" + data.previousRowMeta.getInteger(r, data.indexOfPort), -1); // DEEM-MOD
      }

      //  String authuser = null; // DEEM-MOD
      if (data.indexOfAuthenticationUser > -1) {
        data.authenticationUser =
            data.previousRowMeta.getString(r, data.indexOfAuthenticationUser); // DEEM-MOD
      }
      //  String authpass = null; // DEEM-MOD
      if (data.indexOfAuthenticationPass > -1) {
        data.authenticationPassword =
            data.previousRowMeta.getString(r, data.indexOfAuthenticationPass); // DEEM-MOD
      }

      String subject = null;
      if (data.indexOfSubject > -1) {
        subject = data.previousRowMeta.getString(r, data.indexOfSubject);
      }

      String comment = null;
      if (data.indexOfComment > -1) {
        comment = data.previousRowMeta.getString(r, data.indexOfComment);
      }

      // send email...
      String message =
          sendMail(
              r,
              // servername, // DEEM-MOD
              // port,  // DEEM-MOD
              mailsenderaddress,
              mailsendername,
              maildestination,
              maildestinationCc,
              maildestinationBCc,
              contactperson,
              contactphone,
              // authuser,  // DEEM-MOD
              // authpass,  // DEEM-MOD
              subject,
              comment,
              mailreplyToAddresses);

      if (meta.isAddMessageToOutput()) {
        int index = data.previousRowMeta.indexOfValue(resolve(meta.getMessageOutputField()));
        Object[] outputRowData = RowDataUtil.createResizedCopy(r, data.previousRowMeta.size());
        outputRowData[index] = message;
        putRow(data.previousRowMeta, outputRowData);
      } else {
        putRow(data.previousRowMeta, r);
      }

      if (log.isRowLevel()) {
        logRowlevel(
            BaseMessages.getString(
                PKG,
                "Mail.Log.LineNumber",
                getLinesRead() + " : " + getInputRowMeta().getString(r)));
      }

    } catch (Exception e) {
      if (getTransformMeta().isDoingErrorHandling()) {
        sendToErrorRow = true;
        errorMessage = e.toString();
      } else {
        throw new HopException(BaseMessages.getString(PKG, "Mail.Error.General"), e);
      }
      if (sendToErrorRow) {
        // Simply add this row to the error row
        putError(getInputRowMeta(), r, 1, errorMessage, null, "MAIL001");
      }
    }

    return true;
  }

  public String sendMail(
      Object[] r,
      // String server, // DEEM-MOD
      // int port, // DEEM-MOD
      String senderAddress,
      String senderName,
      String destination,
      String destinationCc,
      String destinationBCc,
      String contactPerson,
      String contactPhone,
      // String authenticationUser,  // DEEM-MOD
      // String authenticationPassword,  // DEEM-MOD
      String mailsubject,
      String comment,
      String replyToAddresses)
      throws Exception {

    // Send an e-mail...
    // create some properties and get the default Session

    if (!data.usingConfig) { // DEEM-MOD
      data.protocol = "smtp"; // DEEM-MOD
      if (meta.isUsingSecureAuthentication()) {
        if (meta.isUseXOAUTH2()) {
          data.props.put("mail.smtp.ssl.enable", "true");
          data.props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        }
        if (meta.getSecureConnectionType().equals("TLS")) {
          // Allow TLS authentication
          data.props.put("mail.smtp.starttls.enable", "true");
        } else if (meta.getSecureConnectionType().equals("TLS 1.2")) {
          // Allow TLS 1.2 authentication
          data.props.put("mail.smtp.starttls.enable", "true");
          data.props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        } else {
          data.protocol = "smtps"; // DEEM-MOD
          // required to get rid of a SSL exception :
          // nested exception is:
          // javax.net.ssl.SSLException: Unsupported record version Unknown
          data.props.put("mail.smtps.quitwait", "false");
        }
      }
      data.props.put("mail." + data.protocol + ".host", data.server); // DEEM-MOD
      if (data.port != -1) {
        data.props.put(
            "mail." + data.protocol + ".port",
            "" + data.port); // DEEM-MOD // needs to be supplied as a string, not as an integer
      }
      if (meta.isUsingAuthentication()) {
        data.props.put("mail." + data.protocol + ".auth", "true"); // DEEM-MOD
      }
    }

    if (isDebug()) {
      data.props.put("mail.debug", "true");
    }

    Session session = Session.getInstance(data.props);
    session.setDebug(isDebug());

    // create a message
    Message msg = new MimeMessage(session);

    // set message priority
    if (meta.isUsePriority()) {
      String priorityInt = "1";
      if (meta.getPriority().equals("low")) {
        priorityInt = "3";
      }
      if (meta.getPriority().equals("normal")) {
        priorityInt = "2";
      }

      msg.setHeader("X-Priority", priorityInt); // (String)int between 1= high and 3 = low.
      msg.setHeader("Importance", meta.getImportance());
      // seems to be needed for MS Outlook.
      // where it returns a string of high /normal /low.
      msg.setHeader("Sensitivity", meta.getSensitivity());
      // Possible values are normal, personal, private, company-confidential
    }

    // set Email sender
    String emailAddress = senderAddress;
    if (!Utils.isEmpty(emailAddress)) {
      // get sender name
      if (!Utils.isEmpty(senderName)) {
        emailAddress = senderName + '<' + emailAddress + '>';
      }
      msg.setFrom(new InternetAddress(emailAddress));
    } else {
      throw new MessagingException(BaseMessages.getString(PKG, "Mail.Error.ReplyEmailNotFilled"));
    }

    // Set reply to
    if (!Utils.isEmpty(replyToAddresses)) {
      // get replay to
      // Split the mail-address: variables separated
      String[] replyAddressList = replyToAddresses.split(" ");
      InternetAddress[] address = new InternetAddress[replyAddressList.length];

      for (int i = 0; i < replyAddressList.length; i++) {
        address[i] = new InternetAddress(replyAddressList[i]);
      }

      // To add the real reply-to
      msg.setReplyTo(address);
    }

    // Split the mail-address: variables separated
    String[] destinations = destination.split(" ");
    InternetAddress[] address = new InternetAddress[destinations.length];
    for (int i = 0; i < destinations.length; i++) {
      address[i] = new InternetAddress(destinations[i]);
    }

    msg.setRecipients(Message.RecipientType.TO, address);

    String realdestinationCc = destinationCc;
    if (!Utils.isEmpty(realdestinationCc)) {
      // Split the mail-address Cc: variables separated
      String[] destinationsCc = realdestinationCc.split(" ");
      InternetAddress[] addressCc = new InternetAddress[destinationsCc.length];
      for (int i = 0; i < destinationsCc.length; i++) {
        addressCc[i] = new InternetAddress(destinationsCc[i]);
      }

      msg.setRecipients(Message.RecipientType.CC, addressCc);
    }

    String realdestinationBCc = destinationBCc;
    if (!Utils.isEmpty(realdestinationBCc)) {
      // Split the mail-address BCc: variables separated
      String[] destinationsBCc = realdestinationBCc.split(" ");
      InternetAddress[] addressBCc = new InternetAddress[destinationsBCc.length];
      for (int i = 0; i < destinationsBCc.length; i++) {
        addressBCc[i] = new InternetAddress(destinationsBCc[i]);
      }

      msg.setRecipients(Message.RecipientType.BCC, addressBCc);
    }

    if (mailsubject != null) {
      msg.setSubject(mailsubject);
    }

    msg.setSentDate(new Date());
    StringBuilder messageText = new StringBuilder();

    if (comment != null) {
      messageText.append(comment).append(Const.CR).append(Const.CR);
    }

    if (meta.getIncludeDate()) {
      messageText
          .append(BaseMessages.getString(PKG, "Mail.Log.Comment.MsgDate") + ": ")
          .append(XmlHandler.date2string(new Date()))
          .append(Const.CR)
          .append(Const.CR);
    }

    if (!meta.isOnlySendComment()
        && (!Utils.isEmpty(contactPerson) || !Utils.isEmpty(contactPhone))) {
      messageText
          .append(BaseMessages.getString(PKG, "Mail.Log.Comment.ContactInfo") + " :")
          .append(Const.CR);
      messageText.append("---------------------").append(Const.CR);
      messageText
          .append(BaseMessages.getString(PKG, "Mail.Log.Comment.PersonToContact") + " : ")
          .append(contactPerson)
          .append(Const.CR);
      messageText
          .append(BaseMessages.getString(PKG, "Mail.Log.Comment.Tel") + "  : ")
          .append(contactPhone)
          .append(Const.CR);
      messageText.append(Const.CR);
    }
    data.parts = new MimeMultipart();

    MimeBodyPart part1 = new MimeBodyPart(); // put the text in the
    // 1st part

    if (meta.isUseHTML()) {
      if (!Utils.isEmpty(meta.getEncoding())) {
        part1.setContent(messageText.toString(), "text/html; " + "charset=" + meta.getEncoding());
      } else {
        part1.setContent(messageText.toString(), "text/html; " + "charset=ISO-8859-1");
      }
    } else {
      part1.setText(messageText.toString());
    }

    data.parts.addBodyPart(part1);

    if (meta.isAttachContentFromField()) {
      // attache file content
      addAttachedContent(
          data.previousRowMeta.getString(r, data.indexOfAttachedFilename),
          data.previousRowMeta.getString(r, data.indexOfAttachedContent));
    } else {
      // attached files
      if (meta.isDynamicFilename()) {
        setAttachedFilesList(r, log);
      } else {
        setAttachedFilesList(null, log);
      }
    }

    // add embedded images
    addImagePart();

    if (data.nrEmbeddedImages > 0 && data.nrattachedFiles == 0) {
      // If we need to embedd images...
      // We need to create a "multipart/related" message.
      // otherwise image will appear as attached file
      data.parts.setSubType("related");
    }

    msg.setContent(data.parts);

    Transport transport = null;
    try {
      transport = session.getTransport(data.protocol); // DEEM-MOD
      String authPass = getPassword(data.authenticationPassword); // DEEM-MOD
      if (data.usingAuthentication) {
        if (data.port != -1) {
          transport.connect(
              Const.NVL(data.server, ""),
              data.port,
              Const.NVL(data.authenticationUser, ""),
              authPass); // DEEM-MOD
        } else {
          transport.connect(
              Const.NVL(data.server, ""),
              Const.NVL(data.authenticationUser, ""),
              authPass); // DEEM-MOD
        }
      } else {
        transport.connect();
      }
      transport.sendMessage(msg, msg.getAllRecipients());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
    ByteArrayOutputStream emlMsg = new ByteArrayOutputStream();
    msg.writeTo(emlMsg);
    emlMsg.close();
    return emlMsg.toString();
  }

  // DEEM-MOD
  public String getPassword(String authPassword) {
    return Encr.decryptPasswordOptionallyEncrypted(resolve(Const.NVL(authPassword, "")));
  }

  private void setAttachedFilesList(Object[] r, ILogChannel log) throws Exception {
    String realSourceFileFoldername = null;
    String realSourceWildcard = null;
    FileObject sourcefile = null;
    FileObject file = null;

    ZipOutputStream zipOutputStream = null;
    File masterZipfile = null;

    if (meta.isZipFilenameDynamic()) {
      data.ZipFilename = data.previousRowMeta.getString(r, data.indexOfDynamicZipFilename);
    }

    try {

      if (meta.isDynamicFilename()) {
        // dynamic attached filenames
        if (data.indexOfSourceFilename > -1) {
          realSourceFileFoldername = data.previousRowMeta.getString(r, data.indexOfSourceFilename);
        }

        if (data.indexOfSourceWildcard > -1) {
          realSourceWildcard = data.previousRowMeta.getString(r, data.indexOfSourceWildcard);
        }

      } else {
        // static attached filenames
        realSourceFileFoldername = data.realSourceFileFoldername;
        realSourceWildcard = data.realSourceWildcard;
      }

      if (!Utils.isEmpty(realSourceFileFoldername)) {
        sourcefile = HopVfs.getFileObject(realSourceFileFoldername, variables);
        if (sourcefile.exists()) {
          long fileSize = 0;
          FileObject[] list = null;
          if (sourcefile.getType() == FileType.FILE) {
            list = new FileObject[1];
            list[0] = sourcefile;
          } else {
            list =
                sourcefile.findFiles(
                    new TextFileSelector(sourcefile.toString(), realSourceWildcard));
          }
          if (list.length > 0) {

            boolean zipFiles = meta.isZipFiles();
            if (zipFiles && data.zipFileLimit == 0) {
              masterZipfile =
                  new File(
                      System.getProperty("java.io.tmpdir")
                          + Const.FILE_SEPARATOR
                          + data.ZipFilename);

              zipOutputStream = new ZipOutputStream(new FileOutputStream(masterZipfile));
            }

            for (int i = 0; i < list.length; i++) {

              file = HopVfs.getFileObject(HopVfs.getFilename(list[i]), variables);

              if (zipFiles) {

                if (data.zipFileLimit == 0) {
                  ZipEntry zipEntry = new ZipEntry(file.getName().getBaseName());
                  zipOutputStream.putNextEntry(zipEntry);

                  // Now put the content of this file into this archive...
                  BufferedInputStream inputStream =
                      new BufferedInputStream(file.getContent().getInputStream());
                  int c;
                  while ((c = inputStream.read()) >= 0) {
                    zipOutputStream.write(c);
                  }
                  inputStream.close();
                  zipOutputStream.closeEntry();
                } else {
                  fileSize += file.getContent().getSize();
                }
              } else {
                addAttachedFilePart(file);
              }
            } // end for
            if (zipFiles) {
              if (isDebug()) {
                logDebug(BaseMessages.getString(PKG, "Mail.Log.FileSize", "" + fileSize));
              }
              if (isDebug()) {
                logDebug(BaseMessages.getString(PKG, "Mail.Log.LimitSize", "" + data.zipFileLimit));
              }

              if (data.zipFileLimit > 0 && fileSize > data.zipFileLimit) {

                masterZipfile =
                    new File(
                        System.getProperty("java.io.tmpdir")
                            + Const.FILE_SEPARATOR
                            + data.ZipFilename);

                zipOutputStream = new ZipOutputStream(new FileOutputStream(masterZipfile));

                for (int i = 0; i < list.length; i++) {

                  file = HopVfs.getFileObject(HopVfs.getFilename(list[i]), variables);

                  ZipEntry zipEntry = new ZipEntry(file.getName().getBaseName());
                  zipOutputStream.putNextEntry(zipEntry);

                  // Now put the content of this file into this archive...
                  BufferedInputStream inputStream =
                      new BufferedInputStream(file.getContent().getInputStream());
                  int c;
                  while ((c = inputStream.read()) >= 0) {
                    zipOutputStream.write(c);
                  }
                  inputStream.close();
                  zipOutputStream.closeEntry();
                }
              }
              if (data.zipFileLimit > 0 && fileSize > data.zipFileLimit || data.zipFileLimit == 0) {
                file = HopVfs.getFileObject(masterZipfile.getAbsolutePath(), variables);
                addAttachedFilePart(file);
              }
            }
          }
        } else {
          logError(
              BaseMessages.getString(
                  PKG, "Mail.Error.SourceFileFolderNotExists", realSourceFileFoldername));
        }
      }
    } catch (Exception e) {
      logError(e.getMessage());
    } finally {
      if (sourcefile != null) {
        try {
          sourcefile.close();
        } catch (Exception e) {
          // Ignore errors
        }
      }
      if (file != null) {
        try {
          file.close();
        } catch (Exception e) {
          // Ignore errors
        }
      }

      if (zipOutputStream != null) {
        try {
          zipOutputStream.finish();
          zipOutputStream.close();
        } catch (IOException e) {
          logError("Unable to close attachement zip file archive : " + e.toString());
        }
      }
    }
  }

  private void addAttachedFilePart(FileObject file) throws Exception {
    // create a data source

    MimeBodyPart files = new MimeBodyPart();
    // create a data source
    URLDataSource fds = new URLDataSource(file.getURL());
    // get a data Handler to manipulate this file type
    files.setDataHandler(new DataHandler(fds));
    // include the file in the data source
    files.setFileName(file.getName().getBaseName());
    // insist on base64 to preserve line endings
    files.addHeader("Content-Transfer-Encoding", "base64");
    // add the part with the file in the BodyPart()
    data.parts.addBodyPart(files);
    if (isDetailed()) {
      logDetailed(BaseMessages.getString(PKG, "Mail.Log.AttachedFile", fds.getName()));
    }
  }

  private void addAttachedContent(String filename, String fileContent) throws Exception {
    // create a data source

    MimeBodyPart mbp = new MimeBodyPart();
    // get a data Handler to manipulate this file type
    mbp.setDataHandler(
        new DataHandler(new ByteArrayDataSource(fileContent.getBytes(), "application/x-any")));
    // include the file in the data source
    mbp.setFileName(filename);
    // add the part with the file in the BodyPart()
    data.parts.addBodyPart(mbp);
  }

  private void addImagePart() throws Exception {
    data.nrEmbeddedImages = 0;
    if (data.embeddedMimePart != null && !data.embeddedMimePart.isEmpty()) {
      for (Iterator<MimeBodyPart> i = data.embeddedMimePart.iterator(); i.hasNext(); ) {
        MimeBodyPart part = i.next();
        data.parts.addBodyPart(part);
        data.nrEmbeddedImages++;
      }
    }
  }

  private class TextFileSelector implements FileSelector {
    String fileWildcard = null;
    String sourceFolder = null;

    public TextFileSelector(String sourcefolderin, String filewildcard) {
      if (!Utils.isEmpty(sourcefolderin)) {
        sourceFolder = sourcefolderin;
      }

      if (!Utils.isEmpty(filewildcard)) {
        fileWildcard = filewildcard;
      }
    }

    @Override
    public boolean includeFile(FileSelectInfo info) {
      boolean returncode = false;
      try {
        if (!info.getFile().toString().equals(sourceFolder)) {
          // Pass over the Base folder itself
          String shortFilename = info.getFile().getName().getBaseName();

          if (info.getFile().getParent().equals(info.getBaseFolder())
              || (!info.getFile().getParent().equals(info.getBaseFolder())
                  && meta.isIncludeSubFolders())) {
            if ((info.getFile().getType() == FileType.FILE && fileWildcard == null)
                || (info.getFile().getType() == FileType.FILE
                    && fileWildcard != null
                    && getFileWildcard(shortFilename, fileWildcard))) {
              returncode = true;
            }
          }
        }
      } catch (Exception e) {
        logError(
            BaseMessages.getString(
                PKG, "Mail.Error.FindingFiles", info.getFile().toString(), e.getMessage()));
        returncode = false;
      }
      return returncode;
    }

    @Override
    public boolean traverseDescendents(FileSelectInfo info) {
      return true;
    }
  }

  /**********************************************************
   *
   * @param selectedfile
   * @param wildcard
   * @return True if the selectedfile matches the wildcard
   **********************************************************/
  private boolean getFileWildcard(String selectedfile, String wildcard) {
    Pattern pattern = null;
    boolean getIt = true;

    if (!Utils.isEmpty(wildcard)) {
      pattern = Pattern.compile(wildcard);
      // First see if the file matches the regular expression!
      if (pattern != null) {
        Matcher matcher = pattern.matcher(selectedfile);
        getIt = matcher.matches();
      }
    }

    return getIt;
  }

  @Override
  public boolean init() {

    if (super.init()) {
      // Add init code here.
      return true;
    }
    return false;
  }

  @Override
  public void dispose() {

    if (data.embeddedMimePart != null) {
      data.embeddedMimePart.clear();
    }
    data.parts = null;
    super.dispose();
  }
}
