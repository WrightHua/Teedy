package com.sismics.docs.rest;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Date;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.mime.MimeType;
import com.sismics.util.mime.MimeTypeUtil;

/**
 * Exhaustive test of the file resource.
 * 
 * @author bgamard
 */
public class TestFileResource extends BaseJerseyTest {
    /**
     * Test the file resource.
     * 
     * @throws Exception
     */
    @Test
    public void testFileResource() throws Exception {
        // Login file1
        clientUtil.createUser("file1");
        String file1AuthenticationToken = clientUtil.login("file1");
        
        // Create a document
        long create1Date = new Date().getTime();
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .put(Entity.form(new Form()
                        .param("title", "File test document 1")
                        .param("language", "eng")
                        .param("create_date", Long.toString(create1Date))), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);
        
        // Add a file
        String file1Id = null;
        try (InputStream is = Resources.getResource("file/PIA00452.jpg").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "PIA00452.jpg");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                        .put(Entity.entity(multiPart.field("id", document1Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file1Id = json.getString("id");
                Assert.assertNotNull(file1Id);
            }
        }
        
        // Add a file
        String file2Id = null;
        try (InputStream is = Resources.getResource("file/PIA00452.jpg").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "PIA00452.jpg");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                        .put(Entity.entity(multiPart.field("id", document1Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file2Id = json.getString("id");
                Assert.assertNotNull(file2Id);
            }
        }
        
        // Get the file data
        Response response = target().path("/file/" + file1Id + "/data").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get();
        InputStream is = (InputStream) response.getEntity();
        byte[] fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(MimeType.IMAGE_JPEG, MimeTypeUtil.guessMimeType(fileBytes));
        Assert.assertTrue(fileBytes.length > 0);
        
        // Get the thumbnail data
        response = target().path("/file/" + file1Id + "/data")
                .queryParam("size", "thumb")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        is = (InputStream) response.getEntity();
        fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(MimeType.IMAGE_JPEG, MimeTypeUtil.guessMimeType(fileBytes));
        Assert.assertTrue(fileBytes.length > 0);
        
        // Get the web data
        response = target().path("/file/" + file1Id + "/data")
                .queryParam("size", "web")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get();
        Assert.assertEquals(Status.OK, Status.fromStatusCode(response.getStatus()));
        is = (InputStream) response.getEntity();
        fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(MimeType.IMAGE_JPEG, MimeTypeUtil.guessMimeType(fileBytes));
        Assert.assertTrue(fileBytes.length > 0);
        
        // Check that the files are not readable directly from FS
        java.io.File storedFile = Paths.get(DirectoryUtil.getStorageDirectory().getPath(), file1Id).toFile();
        try (InputStream storedFileInputStream = new BufferedInputStream(new FileInputStream(storedFile))) {
            Assert.assertNull(MimeTypeUtil.guessMimeType(storedFileInputStream));
        }
        
        // Get all files from a document
        json = target().path("/file/list")
                .queryParam("id", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(2, files.size());
        Assert.assertEquals(file1Id, files.getJsonObject(0).getString("id"));
        Assert.assertEquals(file2Id, files.getJsonObject(1).getString("id"));
        
        // Reorder files
        json = target().path("/file/reorder").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .post(Entity.form(new Form()
                        .param("id", document1Id)
                        .param("order", file2Id)
                        .param("order", file1Id)), JsonObject.class);

        // Get all files from a document
        json = target().path("/file/list")
                .queryParam("id", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(2, files.size());
        Assert.assertEquals(file2Id, files.getJsonObject(0).getString("id"));
        Assert.assertEquals(file1Id, files.getJsonObject(1).getString("id"));
        
        // Get a ZIP from all files
        response = target().path("/file/zip")
                .queryParam("id", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get();
        is = (InputStream) response.getEntity();
        fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(MimeType.APPLICATION_ZIP, MimeTypeUtil.guessMimeType(fileBytes));
        
        // Deletes a file
        json = target().path("/file/" + file1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Get the file data (not found)
        response = target().path("/file/" + file1Id + "/data").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get();
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(response.getStatus()));
        
        // Check that files are deleted from FS
        storedFile = Paths.get(DirectoryUtil.getStorageDirectory().getPath(), file1Id).toFile();
        java.io.File webFile = Paths.get(DirectoryUtil.getStorageDirectory().getPath(), file1Id + "_web").toFile();
        java.io.File thumbnailFile = Paths.get(DirectoryUtil.getStorageDirectory().getPath(), file1Id + "_thumb").toFile();
        Assert.assertFalse(storedFile.exists());
        Assert.assertFalse(webFile.exists());
        Assert.assertFalse(thumbnailFile.exists());
        
        // Get all files from a document
        json = target().path("/file/list")
                .queryParam("id", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file1AuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(1, files.size());
    }
    
    @Test
    public void testOrphanFile() throws Exception {
        // Login file1
        clientUtil.createUser("file2");
        String file2AuthenticationToken = clientUtil.login("file2");
        
        // Add a file
        String file1Id = null;
        try (InputStream is = Resources.getResource("file/PIA00452.jpg").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "PIA00452.jpg");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                JsonObject json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                        .put(Entity.entity(multiPart.bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file1Id = json.getString("id");
                Assert.assertNotNull(file1Id);
            }
        }
        
        // Get all orphan files
        JsonObject json = target().path("/file/list").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .get(JsonObject.class);
        JsonArray files = json.getJsonArray("files");
        Assert.assertEquals(1, files.size());
        
        // Get the file data
        Response response = target().path("/file/" + file1Id + "/data").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .get();
        InputStream is = (InputStream) response.getEntity();
        byte[] fileBytes = ByteStreams.toByteArray(is);
        Assert.assertEquals(MimeType.IMAGE_JPEG, MimeTypeUtil.guessMimeType(fileBytes));
        Assert.assertEquals(163510, fileBytes.length);
        
        // Create a document
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .put(Entity.form(new Form()
                        .param("title", "File test document 1")
                        .param("language", "eng")), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);
        
        // Attach a file to a document
        json = target().path("/file/" + file1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .post(Entity.form(new Form()
                        .param("id", document1Id)), JsonObject.class);
        
        // Get all files from a document
        json = target().path("/file/list")
                .queryParam("id", document1Id)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .get(JsonObject.class);
        files = json.getJsonArray("files");
        Assert.assertEquals(1, files.size());
        
        // Add a file
        String file2Id = null;
        try (InputStream is0 = Resources.getResource("file/PIA00452.jpg").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is0, "PIA00452.jpg");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                        .put(Entity.entity(multiPart.bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file2Id = json.getString("id");
                Assert.assertNotNull(file2Id);
            }
        }
        
        // Deletes a file
        json = target().path("/file/" + file2Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, file2AuthenticationToken)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
    }
}