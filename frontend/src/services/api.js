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

export async function registerUser(username, email, password) {
  return request("/auth/register", {
    method: "POST",
    body: JSON.stringify({
      username,
      email,
      password,
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

export async function getCurrentUser(username) {
  return request(`/users/me?username=${encodeURIComponent(username)}`);
}

export async function getOnlineUsers() {
  const response = await fetch(`${API_BASE_URL}/api/users/online`);

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || "Unable to fetch online users");
  }

  return data;
}

export async function getGroups() {
  return request("/groups");
}
