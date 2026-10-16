
package model;

public class User {

    private String username;
    private String password;
    private String email;
    private String mobileNumber;

    public User() {
    }

    // Existing constructor — preserved for compatibility
    public User(
            String username,
            String password,
            String email) {

        this.username = username;
        this.password = password;
        this.email = email;
    }

    // New constructor with mobile number
    public User(
            String username,
            String password,
            String email,
            String mobileNumber) {

        this.username = username;
        this.password = password;
        this.email = email;
        this.mobileNumber = mobileNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                '}';
    }
}
