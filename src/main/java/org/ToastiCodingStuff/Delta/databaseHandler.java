package org.ToastiCodingStuff.Delta;

import java.io.PrintStream;
import java.sql.*;

public class databaseHandler {

    private final Connection connection;
    public databaseHandler() {
        Connection connection1;
        try {
            connection1 = DriverManager.getConnection("jdbc:sqlite:server.db");
        }catch (SQLException e) {
            connection1 = null;
            System.out.println(e);
        }
        this.connection = connection1;
    }

    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getLogChannelID (String guildID) {
        try {
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM log_channels");
            while (rs.next()) {
                if (rs.getString("guildid").equals(guildID))
                {
                    return rs.getString("channelid");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error: " + e;
        }
        return "Nothing found";
    }

    public boolean hasLogChannel (String guildID) {
        try {
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM log_channels");
            while (rs.next()) {
                if (rs.getString("guildid").equals(guildID))
                {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public String setLogChannel (String guildID, String channelID) {
        try {
            connection.setAutoCommit(false);
            if (hasLogChannel(guildID)) {
                String setLogChannel = "UPDATE log_channels SET channelid=? WHERE guildid=?";
                PreparedStatement updateLogChannel = connection.prepareStatement(setLogChannel);
                updateLogChannel.setString(1, channelID);
                updateLogChannel.setString(2, guildID);
                updateLogChannel.executeUpdate();
                connection.commit();
                return channelID;
            } else {
                String setLogChannel = "INSERT INTO log_channels(guildid, channelid) VALUES(?,?)";
                PreparedStatement updateLogChannel = connection.prepareStatement(setLogChannel);
                updateLogChannel.setString(1, guildID);
                updateLogChannel.setString(2, channelID);
                updateLogChannel.executeQuery();
                connection.commit();
                return channelID;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error";
        }
    }
}
