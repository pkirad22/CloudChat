package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import database.MongoDBConnection;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

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

            List<String> members = new ArrayList<>();

            members.add(creator);

            Document document = new Document(
                    "groupName",
                    groupName)
                    .append(
                            "creator",
                            creator)
                    .append(
                            "members",
                            members);

            groups.insertOne(document);

            System.out.println(
                    "Group saved to MongoDB: "
                            + groupName);

            return true;

        } catch (Exception e) {

            System.out.println(
                    "Group creation failed: "
                            + e.getMessage());

            return false;
        }
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

            groups.updateOne(
                    eq("groupName",
                            groupName),
                    pull(
                            "members",
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

            return new ArrayList<>(members);

        } catch (Exception e) {

            System.out.println(
                    "Getting group members failed: "
                            + e.getMessage());

            return new ArrayList<>();
        }
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