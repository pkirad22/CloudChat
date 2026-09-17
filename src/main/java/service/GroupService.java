package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import database.MongoDBConnection;

import org.bson.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.GroupLayout.Group;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;

public class GroupService {

        private final MongoCollection<Document> groups;

        public GroupService() {

                MongoDatabase database = MongoDBConnection.getDatabase();

                groups = database.getCollection("groups");
        }

        // =========================================================
        // CREATE GROUP
        // =========================================================

        public boolean createGroup(
                        String groupName,
                        String creator) {

                try {

                        if (groupName == null
                                        || creator == null
                                        || groupName.trim().isEmpty()
                                        || creator.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        creator = creator.trim();

                        // Check if group already exists
                        Document existingGroup = groups.find(
                                        eq("groupName", groupName))
                                        .first();

                        if (existingGroup != null) {

                                System.out.println(
                                                "Group already exists: "
                                                                + groupName);

                                return false;
                        }

                        // =================================================
                        // MEMBERS
                        // =================================================

                        List<String> members = new ArrayList<>();

                        members.add(creator);

                        // =================================================
                        // OWNERS
                        // Creator automatically becomes first owner
                        // =================================================

                        List<String> owners = new ArrayList<>();

                        owners.add(creator);

                        // =================================================
                        // PENDING JOIN REQUESTS
                        // =================================================

                        List<String> pendingRequests = new ArrayList<>();

                        // =================================================
                        // JOIN CODE
                        // =================================================

                        String joinCode = generateUniqueJoinCode();

                        // =================================================
                        // CREATE MONGODB DOCUMENT
                        // =================================================

                        Document document = new Document(
                                        "groupName",
                                        groupName)
                                        .append(
                                                        "creator",
                                                        creator)
                                        .append(
                                                        "owners",
                                                        owners)
                                        .append(
                                                        "members",
                                                        members)
                                        .append(
                                                        "joinCode",
                                                        joinCode)
                                        .append(
                                                        "accessMode",
                                                        "REQUEST")
                                        .append(
                                                        "pendingRequests",
                                                        pendingRequests)
                                        .append(
                                                        "createdAt",
                                                        LocalDateTime.now()
                                                                        .toString());

                        groups.insertOne(document);

                        System.out.println();
                        System.out.println(
                                        "Group saved to MongoDB: "
                                                        + groupName);

                        System.out.println(
                                        "Group owner: "
                                                        + creator);

                        System.out.println(
                                        "Group join code: "
                                                        + joinCode);

                        System.out.println(
                                        "Group access mode: REQUEST");

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Group creation failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // GENERATE UNIQUE JOIN CODE
        // =========================================================

        private String generateUniqueJoinCode() {

                String joinCode;

                do {

                        joinCode = createJoinCode();

                } while (groups.find(
                                eq("joinCode",
                                                joinCode))
                                .first() != null);

                return joinCode;
        }

        // =========================================================
        // CREATE JOIN CODE
        // =========================================================

        private String createJoinCode() {

                String uuid = UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .toUpperCase();

                return uuid.substring(0, 4)
                                + "-"
                                + uuid.substring(4, 8);
        }

        // =========================================================
        // ADD MEMBER
        // =========================================================

        public boolean addMember(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName))
                                        .first();

                        if (group == null) {

                                return false;
                        }

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null) {

                                members = new ArrayList<>();
                        }

                        // Avoid duplicate members
                        if (members.contains(username)) {

                                return true;
                        }

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        addToSet(
                                                        "members",
                                                        username));

                        // Remove pending request if present
                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        pull(
                                                        "pendingRequests",
                                                        username));

                        System.out.println(
                                        "Member saved to MongoDB: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Adding member failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // REMOVE MEMBER
        // =========================================================

        public boolean removeMember(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName))
                                        .first();

                        if (group == null) {

                                return false;
                        }

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null
                                        || !members.contains(username)) {

                                return false;
                        }

                        List<String> owners = group.getList(
                                        "owners",
                                        String.class);

                        // =============================================
                        // Prevent removing the last owner
                        // =============================================

                        if (owners != null
                                        && owners.contains(username)
                                        && owners.size() <= 1) {

                                System.out.println(
                                                "Cannot remove the last owner: "
                                                                + username);

                                return false;
                        }

                        // =============================================
                        // Remove from members
                        // =============================================

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        pull(
                                                        "members",
                                                        username));

                        // =============================================
                        // Remove from owners if applicable
                        // =============================================

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        pull(
                                                        "owners",
                                                        username));

                        System.out.println(
                                        "Member removed from MongoDB: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Removing member failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // GET MEMBERS
        // =========================================================

        public List<String> getMembers(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return new ArrayList<>();
                        }

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName.trim()))
                                        .first();

                        if (group == null) {

                                return new ArrayList<>();
                        }

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null) {

                                return new ArrayList<>();
                        }

                        return new ArrayList<>(
                                        members);

                } catch (Exception e) {

                        System.out.println(
                                        "Getting group members failed: "
                                                        + e.getMessage());

                        return new ArrayList<>();
                }
        }

        // =========================================================
        // GET OWNERS
        // =========================================================

        public List<String> getOwners(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return new ArrayList<>();
                        }

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName.trim()))
                                        .first();

                        if (group == null) {

                                return new ArrayList<>();
                        }

                        List<String> owners = group.getList(
                                        "owners",
                                        String.class);

                        if (owners == null) {

                                return new ArrayList<>();
                        }

                        return new ArrayList<>(
                                        owners);

                } catch (Exception e) {

                        System.out.println(
                                        "Getting group owners failed: "
                                                        + e.getMessage());

                        return new ArrayList<>();
                }
        }

        // =========================================================
        // CHECK OWNER
        // =========================================================

        public boolean isOwner(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null) {

                        return false;
                }

                List<String> owners = getOwners(groupName);

                return owners.contains(
                                username.trim());
        }

        // =========================================================
        // ADD OWNER
        // =========================================================

        public boolean addOwner(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName))
                                        .first();

                        if (group == null) {

                                return false;
                        }

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null
                                        || !members.contains(username)) {

                                System.out.println(
                                                "Cannot make non-member an owner: "
                                                                + username);

                                return false;
                        }

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        addToSet(
                                                        "owners",
                                                        username));

                        System.out.println(
                                        "Owner added: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Adding owner failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // REMOVE OWNER
        // =========================================================

        public boolean removeOwner(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName))
                                        .first();

                        if (group == null) {

                                return false;
                        }

                        List<String> owners = group.getList(
                                        "owners",
                                        String.class);

                        if (owners == null
                                        || !owners.contains(username)) {

                                return false;
                        }

                        // =============================================
                        // Never allow the group to have zero owners
                        // =============================================

                        if (owners.size() <= 1) {

                                System.out.println(
                                                "Cannot remove the last owner from group: "
                                                                + groupName);

                                return false;
                        }

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        pull(
                                                        "owners",
                                                        username));

                        System.out.println(
                                        "Owner removed: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Removing owner failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // PROMOTE MEMBER TO OWNER
        // =========================================================

        public boolean promoteToOwner(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null) {

                        return false;
                }

                groupName = groupName.trim();
                username = username.trim();

                if (!getMembers(groupName)
                                .contains(username)) {

                        System.out.println(
                                        "Cannot promote non-member: "
                                                        + username);

                        return false;
                }

                if (isOwner(
                                groupName,
                                username)) {

                        System.out.println(
                                        username
                                                        + " is already an owner.");

                        return true;
                }

                return addOwner(
                                groupName,
                                username);
        }

        // =========================================================
        // DEMOTE OWNER TO MEMBER
        // =========================================================

        public boolean demoteOwner(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null) {

                        return false;
                }

                groupName = groupName.trim();
                username = username.trim();

                if (!isOwner(
                                groupName,
                                username)) {

                        return false;
                }

                return removeOwner(
                                groupName,
                                username);
        }

        // =========================================================
        // CHECK MEMBER
        // =========================================================

        public boolean isMember(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null) {

                        return false;
                }

                return getMembers(groupName)
                                .contains(username.trim());
        }

        // =========================================================
        // CHECK GROUP EXISTS
        // =========================================================

        public boolean groupExists(
                        String groupName) {

                if (groupName == null
                                || groupName.trim().isEmpty()) {

                        return false;
                }

                return groups.find(
                                eq("groupName",
                                                groupName.trim()))
                                .first() != null;
        }

        // =========================================================
        // GET JOIN CODE
        // =========================================================

        public String getJoinCode(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return null;
                        }

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName.trim()))
                                        .first();

                        if (group == null) {

                                return null;
                        }

                        return group.getString(
                                        "joinCode");

                } catch (Exception e) {

                        System.out.println(
                                        "Getting join code failed: "
                                                        + e.getMessage());

                        return null;
                }
        }

        // =========================================================
        // GET ACCESS MODE
        // =========================================================

        public String getAccessMode(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return null;
                        }

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName.trim()))
                                        .first();

                        if (group == null) {

                                return null;
                        }

                        String accessMode = group.getString(
                                        "accessMode");

                        if (accessMode == null) {

                                return "REQUEST";
                        }

                        return accessMode;

                } catch (Exception e) {

                        System.out.println(
                                        "Getting access mode failed: "
                                                        + e.getMessage());

                        return null;
                }
        }

        // =========================================================
        // GET PENDING REQUESTS
        // =========================================================

        public List<String> getPendingRequests(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return new ArrayList<>();
                        }

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName.trim()))
                                        .first();

                        if (group == null) {

                                return new ArrayList<>();
                        }

                        List<String> requests = group.getList(
                                        "pendingRequests",
                                        String.class);

                        if (requests == null) {

                                return new ArrayList<>();
                        }

                        return new ArrayList<>(
                                        requests);

                } catch (Exception e) {

                        System.out.println(
                                        "Getting pending requests failed: "
                                                        + e.getMessage());

                        return new ArrayList<>();
                }
        }

        // =========================================================
        // APPROVE JOIN REQUEST
        // =========================================================

        public boolean approveJoinRequest(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName", groupName))
                                        .first();

                        if (group == null) {

                                System.out.println(
                                                "[DB] Group not found: "
                                                                + groupName);

                                return false;
                        }

                        List<String> pendingRequests = group.getList(
                                        "pendingRequests",
                                        String.class);

                        if (pendingRequests == null
                                        || !pendingRequests.contains(username)) {

                                return false;
                        }

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null) {
                                members = new ArrayList<>();
                        }

                        if (members.contains(username)) {

                                return false;
                        }

                        members.add(username);

                        pendingRequests.remove(username);

                        groups.updateOne(
                                        eq("groupName", groupName),
                                        Updates.combine(
                                                        Updates.set(
                                                                        "members",
                                                                        members),
                                                        Updates.set(
                                                                        "pendingRequests",
                                                                        pendingRequests)));

                        System.out.println(
                                        "Join request approved: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Approving join request failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // REJECT JOIN REQUEST
        // =========================================================

        public boolean rejectJoinRequest(
                        String groupName,
                        String username) {

                try {

                        if (groupName == null
                                        || username == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();

                        Document group = groups.find(
                                        eq("groupName", groupName))
                                        .first();

                        if (group == null) {

                                System.out.println(
                                                "[DB] Group not found: "
                                                                + groupName);

                                return false;
                        }

                        List<String> pendingRequests = group.getList(
                                        "pendingRequests",
                                        String.class);

                        if (pendingRequests == null
                                        || !pendingRequests.contains(username)) {

                                return false;
                        }

                        pendingRequests.remove(username);

                        groups.updateOne(
                                        eq("groupName", groupName),
                                        Updates.set(
                                                        "pendingRequests",
                                                        pendingRequests));

                        System.out.println(
                                        "Join request rejected: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "Rejecting join request failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // DELETE GROUP
        // =========================================================

        public boolean deleteGroup(
                        String groupName) {

                try {

                        if (groupName == null
                                        || groupName.trim().isEmpty()) {

                                return false;
                        }

                        var result = groups.deleteOne(
                                        eq("groupName",
                                                        groupName.trim()));

                        if (result.getDeletedCount() > 0) {

                                System.out.println(
                                                "Group deleted from MongoDB: "
                                                                + groupName);

                                return true;
                        }

                        return false;

                } catch (Exception e) {

                        System.out.println(
                                        "Group deletion failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // ADD JOIN REQUEST
        // =========================================================

        public boolean addJoinRequest(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null
                                || groupName.trim().isEmpty()
                                || username.trim().isEmpty()) {

                        return false;
                }

                groupName = groupName.trim();
                username = username.trim();

                Document group = groups.find(
                                Filters.eq(
                                                "groupName",
                                                groupName))
                                .first();

                if (group == null) {

                        System.out.println(
                                        "[DB] Group not found: "
                                                        + groupName);

                        return false;
                }

                List<String> members = group.getList(
                                "members",
                                String.class);

                if (members != null
                                && members.contains(username)) {

                        return false;
                }

                List<String> pendingRequests = group.getList(
                                "pendingRequests",
                                String.class);

                if (pendingRequests == null) {

                        pendingRequests = new ArrayList<>();
                }

                if (pendingRequests.contains(username)) {

                        return false;
                }

                pendingRequests.add(username);

                groups.updateOne(
                                Filters.eq(
                                                "groupName",
                                                groupName),
                                Updates.set(
                                                "pendingRequests",
                                                pendingRequests));

                System.out.println(
                                "Join request added: "
                                                + username
                                                + " -> "
                                                + groupName);

                return true;
        }

        // =========================================================
        // CHECK JOIN REQUEST
        // =========================================================

        public boolean hasJoinRequest(
                        String groupName,
                        String username) {

                if (groupName == null
                                || username == null
                                || groupName.trim().isEmpty()
                                || username.trim().isEmpty()) {

                        return false;
                }

                groupName = groupName.trim();
                username = username.trim();

                Document group = groups.find(
                                Filters.eq(
                                                "groupName",
                                                groupName))
                                .first();

                if (group == null) {
                        return false;
                }

                List<String> pendingRequests = group.getList(
                                "pendingRequests",
                                String.class);

                return pendingRequests != null
                                && pendingRequests.contains(username);
        }

        // =========================================================
        // JOIN GROUP USING JOIN CODE
        // =========================================================

        public boolean joinGroupWithCode(
                        String groupName,
                        String username,
                        String joinCode) {

                try {

                        if (groupName == null
                                        || username == null
                                        || joinCode == null
                                        || groupName.trim().isEmpty()
                                        || username.trim().isEmpty()
                                        || joinCode.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        username = username.trim();
                        joinCode = joinCode.trim();

                        Document group = groups.find(
                                        eq("groupName", groupName))
                                        .first();

                        if (group == null) {

                                System.out.println(
                                                "[DB] Group not found: "
                                                                + groupName);

                                return false;
                        }

                        // -------------------------------------------------
                        // CHECK JOIN CODE
                        // -------------------------------------------------

                        String storedJoinCode = group.getString("joinCode");

                        if (storedJoinCode == null
                                        || !storedJoinCode.equalsIgnoreCase(
                                                        joinCode)) {

                                System.out.println(
                                                "[DB] Invalid join code for group: "
                                                                + groupName);

                                return false;
                        }

                        // -------------------------------------------------
                        // GET MEMBERS
                        // -------------------------------------------------

                        List<String> members = group.getList(
                                        "members",
                                        String.class);

                        if (members == null) {
                                members = new ArrayList<>();
                        }

                        // -------------------------------------------------
                        // ALREADY MEMBER
                        // -------------------------------------------------

                        if (members.contains(username)) {

                                return false;
                        }

                        // -------------------------------------------------
                        // ADD MEMBER
                        // -------------------------------------------------

                        members.add(username);

                        // -------------------------------------------------
                        // REMOVE PENDING REQUEST IF PRESENT
                        // -------------------------------------------------

                        List<String> pendingRequests = group.getList(
                                        "pendingRequests",
                                        String.class);

                        if (pendingRequests == null) {
                                pendingRequests = new ArrayList<>();
                        }

                        pendingRequests.remove(username);

                        // -------------------------------------------------
                        // UPDATE MONGODB
                        // -------------------------------------------------

                        groups.updateOne(
                                        eq("groupName", groupName),
                                        Updates.combine(
                                                        Updates.set(
                                                                        "members",
                                                                        members),
                                                        Updates.set(
                                                                        "pendingRequests",
                                                                        pendingRequests)));

                        System.out.println(
                                        "[DB] User joined group using join code: "
                                                        + username
                                                        + " -> "
                                                        + groupName);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[DB] Join using code failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // SET ACCESS MODE
        // =========================================================

        public boolean setAccessMode(
                        String groupName,
                        String accessMode) {

                try {

                        if (groupName == null
                                        || accessMode == null
                                        || groupName.trim().isEmpty()
                                        || accessMode.trim().isEmpty()) {

                                return false;
                        }

                        groupName = groupName.trim();
                        accessMode = accessMode.trim().toUpperCase();

                        // =============================================
                        // VALID ACCESS MODES
                        // =============================================

                        if (!accessMode.equals("REQUEST")
                                        && !accessMode.equals("CODE")
                                        && !accessMode.equals("OPEN")) {

                                System.out.println(
                                                "[DB] Invalid access mode: "
                                                                + accessMode);

                                return false;
                        }

                        // =============================================
                        // CHECK GROUP EXISTS
                        // =============================================

                        Document group = groups.find(
                                        eq("groupName",
                                                        groupName))
                                        .first();

                        if (group == null) {

                                System.out.println(
                                                "[DB] Group not found: "
                                                                + groupName);

                                return false;
                        }

                        // =============================================
                        // UPDATE ACCESS MODE
                        // =============================================

                        groups.updateOne(
                                        eq("groupName",
                                                        groupName),
                                        Updates.set(
                                                        "accessMode",
                                                        accessMode));

                        System.out.println(
                                        "[DB] Group access mode changed: "
                                                        + groupName
                                                        + " -> "
                                                        + accessMode);

                        return true;

                } catch (Exception e) {

                        System.out.println(
                                        "[DB] Setting access mode failed: "
                                                        + e.getMessage());

                        return false;
                }
        }

        // =========================================================
        // GET ALL GROUPS
        // =========================================================

        public List<String> getAllGroups() {

                List<String> groupNames = new ArrayList<>();

                try {

                        for (Document document : groups.find()) {

                                String groupName = document.getString(
                                                "groupName");

                                if (groupName != null) {

                                        groupNames.add(
                                                        groupName);
                                }
                        }

                } catch (Exception e) {

                        System.out.println(
                                        "Getting groups failed: "
                                                        + e.getMessage());
                }

                return groupNames;
        }
}