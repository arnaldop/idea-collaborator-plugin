package com.smartbear.collab.util;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.smartbear.collab.common.model.CollabConstants;
import com.smartbear.collab.common.model.impl.*;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by mzumbado on 2/26/15.
 */
public class ChangeListUtils {

    public static List<ChangeList> VcsFileRevisionToChangeList(String rootDirectory, ScmToken scmToken, Map<VcsFileRevision, CommittedChangeList> commits) {
        List<ChangeList> changeLists =  new ArrayList<ChangeList>();
        for (Map.Entry<VcsFileRevision, CommittedChangeList> commit : commits.entrySet()){
            VcsFileRevision fileRevision = commit.getKey();
            CommittedChangeList committedChangeList = commit.getValue();

//            String scmPath = fileRevision.getChangedRepositoryPath().toString();
            CommitInfo commitInfo = new CommitInfo(fileRevision.getCommitMessage(), fileRevision.getRevisionDate(), fileRevision.getAuthor(), false, fileRevision.getRevisionNumber().asString(), "");
            List<Version> versions = new ArrayList<Version>();
            for (Change change : committedChangeList.getChanges()){
                String fileContent = "";
                try {
                    fileContent = change.getAfterRevision().getContent();
                }
                catch (VcsException ve) {

                }
                ContentRevision baseRevision = change.getBeforeRevision();
                BaseVersion baseVersion;
                if (baseRevision == null) {
                    baseVersion = null;
                }
                else {
                    String baseMd5 = "";
                    String baseScmPath = getScmPath(rootDirectory, baseRevision.getFile().getPath());
                    try {
                        baseMd5 = Hashing.getMD5(baseRevision.getContent().getBytes());
                    }
                    catch (VcsException ve){

                    }
                    String baseVersionName = "";
                    baseVersionName = getScmVersionName(scmToken, baseRevision);
                    baseVersion = new BaseVersion(change.getFileStatus().getId(), baseMd5, commitInfo, CollabConstants.SOURCE_TYPE_SCM, baseVersionName, baseScmPath);
                }

                //Version
                String localPath = change.getAfterRevision().getFile().getPath();
                String scmPath = getScmPath(rootDirectory, change.getAfterRevision().getFile().getPath());
                String md5 = Hashing.getMD5(fileContent.getBytes());
                String action = change.getFileStatus().getId();
                String scmVersionName = getScmVersionName(scmToken, change.getAfterRevision());
                Version version = new Version(scmPath, md5, scmVersionName, localPath, action, CollabConstants.SOURCE_TYPE_SCM, baseVersion);

                versions.add(version);
            }

            ChangeList changeList = new ChangeList(scmToken, getConnectionParameters(scmToken),commitInfo, versions);
            changeLists.add(changeList);
        }
        return changeLists;
    }

    public static String getScmVersionName(ScmToken scmToken, ContentRevision revision){
        String scmVersionName = "";
        if (scmToken == ScmToken.GIT) {
            try {
                String gitHash = "blob " + revision.getContent().length() + "\0" + revision.getContent();
                scmVersionName = Hashing.getSHA1(gitHash.getBytes());
            }
            catch (VcsException ve){}
        }
        else if (scmToken == ScmToken.SUBVERSION) {
            scmVersionName = revision.getRevisionNumber().asString();
        }
        return scmVersionName;
    }

    public static Map<String, byte[]> getZipFiles(List<CommittedChangeList> commits) {
        Map<String, byte[]> zips = new HashMap<String, byte[]>();
        for (CommittedChangeList commit : commits){
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            for (Change change : commit.getChanges()){
                if (change.getBeforeRevision() != null){
                    String baseFileContent = "";
                    try {
                        baseFileContent = change.getBeforeRevision().getContent();
                    }
                    catch (VcsException ve) {

                    }
                    String baseMd5 = Hashing.getMD5(baseFileContent.getBytes());
                    ZipEntry baseZipEntry = new ZipEntry(baseMd5);

                    try {
                        zos.putNextEntry(baseZipEntry);
                        zos.write(baseFileContent.getBytes());
                        zos.closeEntry();
                    }
                    catch (IOException ioe) {

                    }
                }
                String fileContent = "";
                try {
                    fileContent = change.getAfterRevision().getContent();
                }
                catch (VcsException ve) {

                }
                String md5 = Hashing.getMD5(fileContent.getBytes());
                ZipEntry zipEntry = new ZipEntry(md5);

                try {
                    zos.putNextEntry(zipEntry);
                    zos.write(fileContent.getBytes());
                    zos.closeEntry();
                }
                catch (IOException ioe) {

                }
            }
            try {
                zos.finish();
                zos.close();
            }
            catch (IOException ioe) {
            }
            zips.put("rev_" + Math.abs(commit.getNumber()) + ".zip", baos.toByteArray());
            try {
                baos.close();
            }
            catch (IOException ioe) {

            }
        }
        return zips;
    }

    private static String getScmPath(String root, String path){
        String result = "";
        if (path.contains(root)){
            result = path.substring(root.length() + 1);
        }
        return result;
    }

    private static List<String> getConnectionParameters(ScmToken scmToken){
        List<String> result = new ArrayList<String>();
        if (scmToken == ScmToken.GIT){
            String currentdirectory = "";
            String globalprovider = "git";
            String scm = "git";
            String gitexe = "";
            result.add(currentdirectory);
            result.add(globalprovider);
            result.add(scm);
            result.add(gitexe);
        }
        else if (scmToken == ScmToken.SUBVERSION){
            String currentdirectory = "";
            String globalprovider = "svn";
            String scm = "svn";
            String svnexe = "";
            result.add(currentdirectory);
            result.add(globalprovider);
            result.add(scm);
            result.add(svnexe);
        }
        return result;
    }
}
