const API_BASE_URL = "http://localhost:9000";

async function request(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  let data;

  try {
    data = await response.json();
  } catch {
    data = {
      success: false,
      message: "Invalid response from server.",
    };
  }

  if (!response.ok) {
    throw new Error(data.message || `Request failed (${response.status})`);
  }

  return data;
}

export async function loginUser(username, password) {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      username,
      password,
    }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Login failed");
  }

  return data;
}

export async function googleLogin(credential) {
  if (!credential || typeof credential !== "string") {
    throw new Error("Google credential is missing.");
  }

  return request("/api/auth/google", {
    method: "POST",
    body: JSON.stringify({
      credential,
    }),
  });
}

export async function registerUser(username, email, password, mobileNumber) {
  return request("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({
      username,
      email,
      password,
      mobileNumber,
    }),
  });
}

export async function checkUsername(username) {
  const response = await fetch(
    `${API_BASE_URL}/api/auth/check-username?username=${encodeURIComponent(username)}`,
  );

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Unable to check username");
  }

  return data;
}

export async function checkEmail(email) {
  return request(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
}

export async function getCurrentUser(username) {
  return request(`/api/users/me?username=${encodeURIComponent(username)}`);
}

export async function getOnlineUsers() {
  const response = await fetch(`${API_BASE_URL}/api/users/online`);

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Unable to fetch online users");
  }

  return data;
}

export async function searchUsers(searchText) {
  const response = await fetch(
    `${API_BASE_URL}/api/users/search?username=${encodeURIComponent(searchText)}`,
  );

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Unable to search users");
  }

  return data;
}

export async function getGroups() {
  return request("/groups");
}

export async function getPrivateMessageHistory(username, otherUser) {
  const response = await fetch(
    `${API_BASE_URL}/api/messages/history?username=${encodeURIComponent(
      username,
    )}&with=${encodeURIComponent(otherUser)}`,
  );

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Unable to load message history");
  }

  return data;
}

export async function sendEmailOtp(email) {
  return request("/api/auth/send-otp", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export async function verifyEmailOtp(email, otp) {
  return request("/api/auth/verify-otp", {
    method: "POST",
    body: JSON.stringify({ email, otp }),
  });
}

export async function updateUsername(
  currentUsername,
  currentPassword,
  newUsername,
) {
  return request("/api/users/update-username", {
    method: "PUT",
    body: JSON.stringify({
      currentUsername,
      currentPassword,
      newUsername,
    }),
  });
}

export async function changePassword(username, currentPassword, newPassword) {
  return request("/api/users/change-password", {
    method: "PUT",
    body: JSON.stringify({
      username,
      currentPassword,
      newPassword,
    }),
  });
}

export async function setGoogleAccountPassword(credential, newPassword) {
  return request("/api/users/google/set-password", {
    method: "POST",
    body: JSON.stringify({
      credential,
      newPassword,
    }),
  });
}

export async function updateGoogleUsername(credential, newUsername) {
  return request("/api/users/google/update-username", {
    method: "PUT",
    body: JSON.stringify({
      credential,
      newUsername,
    }),
  });
}
