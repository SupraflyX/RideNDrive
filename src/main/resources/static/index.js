// Global State Variables
let users = [];
let vehicles = [];
let trips = [];
let rides = [];
let ratings = [];
let currentUser = null;
let authPendingName = "";
let map = null;
let directionsService = null;
let directionsRenderer = null;
let currentMode = "passenger"; // Active view mode



// ==================== NAVIGATION & SIDEBAR LOGIC ====================
function navigateTo(sectionId) {
    // Hide all view sections
    document.querySelectorAll('.view-section').forEach(section => {
        section.style.display = 'none';
    });
    
    // Show the targeted section
    const target = document.getElementById(sectionId);
    if (target) {
        target.style.display = 'block';
    }
    
    // Update active class on sidebar links
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
        if (link.dataset.target === sectionId) {
            link.classList.add('active');
            // Update header title
            document.getElementById('current-view-title').textContent = link.textContent.replace(/[\u1000-\uFFFF]+/, '').trim();
        }
    });
}

function updateDashboardVisibility() {
    const isPassenger = (currentUser.role === 'PASSENGER');

    document.getElementById('nav-passenger').style.display = isPassenger ? 'flex' : 'none';
    document.getElementById('nav-driver').style.display = !isPassenger ? 'flex' : 'none';

    // Real-app landing: passengers land on search, drivers on their trips
    if (isPassenger) {
        navigateTo('view-search-rides');
    } else {
        navigateTo('view-my-trips');
    }

    // FR-13: begin polling the notification inbox for the signed-in user
    startNotificationPolling();
}

// Add click listeners to sidebar navigation
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            const target = link.dataset.target;
            if (target) {
                e.preventDefault();
                navigateTo(target);
                toggleSidebar(false); // auto-close on mobile
            }
        });
    });
    
    // Fix logout button which was moved
    const newLogoutBtn = document.getElementById("btn-logout");
    if (newLogoutBtn) {
        newLogoutBtn.addEventListener("click", () => {
            logout();
        });
    }
});

// Initialize Dashboard
document.addEventListener("DOMContentLoaded", () => {
    setupAuthListeners();
    checkAuthSession();
    setupFormListeners();
    setupPlannerSearchListener();
    setupEditModalListeners();
    setupProfileListeners();
    loadGoogleMapsSdk(); // key served from /api/config (env var), never hardcoded
});

/**
 * Dynamically loads the Google Maps JS SDK using the key exposed by the
 * backend (/api/config, sourced from the GOOGLE_MAPS_API_KEY environment
 * variable). Without a key, the SPA's built-in fallback route visualization
 * is used — no functionality is lost.
 */
async function loadGoogleMapsSdk() {
    try {
        const res = await fetch("/api/config");
        if (!res.ok) return;
        const cfg = await res.json();
        if (!cfg.mapsApiKey) {
            console.info("[Maps] No API key configured — using fallback route visualization.");
            return;
        }
        const s = document.createElement("script");
        s.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(cfg.mapsApiKey)}`;
        s.async = true;
        s.onerror = () => { googleMapsAuthFailed = true; };
        document.head.appendChild(s);
    } catch (e) {
        console.error("[Maps] Failed to fetch runtime config:", e);
    }
}

// Setup Tab Navigation
function setupTabNavigation() {
    const tabButtons = document.querySelectorAll(".tab-btn");
    const tabPanels = document.querySelectorAll(".tab-panel");

    tabButtons.forEach(btn => {
        btn.addEventListener("click", () => {
            tabButtons.forEach(b => b.classList.remove("active"));
            tabPanels.forEach(p => p.classList.remove("active"));

            btn.classList.add("active");
            const tabId = btn.getAttribute("data-tab");
            document.getElementById(tabId).classList.add("active");
        });
    });
}

// Session Check & Authentication Transitions
function checkAuthSession() {
    const userStr = localStorage.getItem("routeshare_user");
    const authContainer = document.getElementById("auth-container");
    const appWrapper = document.getElementById("app-wrapper");

    if (userStr) {
        try {
            currentUser = JSON.parse(userStr);
            currentMode = currentUser.role === "DRIVER" ? "driver" : "passenger";
            authContainer.style.display = "none";
            appWrapper.style.display = "flex";
            updateSessionDisplay();
            loadAllData();
            startNotificationPolling(); // FR-13
        } catch (e) {
            console.error("Failed to parse user session", e);
            localStorage.removeItem("routeshare_user");
            showAuthScreens();
        }
    } else {
        showAuthScreens();
    }
}

function showAuthScreens() {
    stopNotificationPolling(); // FR-13: stop inbox polling on logout
    currentUser = null;
    document.body.className = "";
    const authContainer = document.getElementById("auth-container");
    const appWrapper = document.getElementById("app-wrapper");
    if (authContainer) authContainer.style.display = "flex";
    if (appWrapper) appWrapper.style.display = "none";
    
    const loginScreen = document.getElementById("auth-login-screen");
    const passengerRegisterScreen = document.getElementById("auth-passenger-register-screen");
    const driverRegisterScreen = document.getElementById("auth-driver-register-screen");
    const authTitleText = document.getElementById("auth-title-text");

    if (loginScreen) loginScreen.style.display = "block";
    if (passengerRegisterScreen) passengerRegisterScreen.style.display = "none";
    if (driverRegisterScreen) driverRegisterScreen.style.display = "none";
    if (authTitleText) authTitleText.innerText = "Sign In to DriveNRide";

    // Clear inputs
    const loginName = document.getElementById("login-name");
    const loginPass = document.getElementById("login-password");
    if (loginName) loginName.value = "";
    if (loginPass) loginPass.value = "";

    const regPName = document.getElementById("register-p-name");
    const regPPass = document.getElementById("register-p-password");
    if (regPName) regPName.value = "";
    if (regPPass) regPPass.value = "";

    const regDName = document.getElementById("register-d-name");
    const regDPass = document.getElementById("register-d-password");
    const regDMake = document.getElementById("register-d-make");
    const regDModel = document.getElementById("register-d-model");
    const regDCap = document.getElementById("register-d-capacity");
    if (regDName) regDName.value = "";
    if (regDPass) regDPass.value = "";
    if (regDMake) regDMake.value = "";
    if (regDModel) regDModel.value = "";
    if (regDCap) regDCap.value = "4";

    // Hide role dashboards
    
    
    
    
    
    
}

function updateSessionDisplay() {
    if (!currentUser) return;
    
    // Update header profile card
    document.getElementById("session-avatar").innerText = currentUser.name.charAt(0).toUpperCase();
    document.getElementById("session-username").innerHTML = `<strong>${currentUser.name}</strong>`;
    document.getElementById("session-role-tier").innerText = `${currentUser.role} / ${currentUser.incentiveTier || 'STANDARD'}`;

    // Update form readonly text inputs
    const driverText = currentUser.name;
    
    const vehicleDriverDisplay = document.getElementById("vehicle-driver-display");
    if (vehicleDriverDisplay) vehicleDriverDisplay.value = driverText;

    const tripDriverDisplay = document.getElementById("trip-driver-display");
    if (tripDriverDisplay) tripDriverDisplay.value = driverText;

    const ridePassengerDisplay = document.getElementById("ride-passenger-display");
    if (ridePassengerDisplay) ridePassengerDisplay.value = currentUser.name;

    updateDashboardVisibility();
}



function setupAuthListeners() {
    const loginScreen = document.getElementById("auth-login-screen");
    const passengerRegisterScreen = document.getElementById("auth-passenger-register-screen");
    const driverRegisterScreen = document.getElementById("auth-driver-register-screen");
    const authTitleText = document.getElementById("auth-title-text");

    // Transition links
    document.getElementById("link-goto-register-p").addEventListener("click", (e) => {
        e.preventDefault();
        loginScreen.style.display = "none";
        passengerRegisterScreen.style.display = "block";
        driverRegisterScreen.style.display = "none";
        authTitleText.innerText = "Register Passenger Account";
    });

    document.getElementById("link-goto-register-d").addEventListener("click", (e) => {
        e.preventDefault();
        loginScreen.style.display = "none";
        passengerRegisterScreen.style.display = "none";
        driverRegisterScreen.style.display = "block";
        authTitleText.innerText = "Register Driver Account";
    });

    document.querySelectorAll(".link-back-to-login").forEach(link => {
        link.addEventListener("click", (e) => {
            e.preventDefault();
            loginScreen.style.display = "block";
            passengerRegisterScreen.style.display = "none";
            driverRegisterScreen.style.display = "none";
            authTitleText.innerText = "Sign In to DriveNRide";
        });
    });

    // Handle logout button
    document.getElementById("btn-logout").addEventListener("click", () => {
        localStorage.removeItem("routeshare_user");
        showAuthScreens();
        logConsole("[Session] Logged out successfully.", "text-warning");
    });

    // Handle Sign In submission
    document.getElementById("auth-login-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const name = document.getElementById("login-name").value.trim();
        const password = document.getElementById("login-password").value;

        if (!name || !password) return;
        const loginBtn = e.target.querySelector('button[type="submit"]');
        setBtnLoading(loginBtn, true);
        try {
            const res = await fetch("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name, password })
            });

            const data = await res.json();

            if (res.ok) {
                localStorage.setItem("routeshare_user", JSON.stringify(data));
                currentUser = data;
                currentMode = currentUser.role === "DRIVER" ? "driver" : "passenger";
                document.getElementById("login-name").value = "";
                document.getElementById("login-password").value = "";
                
                // Transition to application
                document.getElementById("auth-container").style.display = "none";
                document.getElementById("app-wrapper").style.display = "flex";
                updateSessionDisplay();
                logConsole(`[Session] Authenticated as ${currentUser.name} (${currentUser.role})`, "text-success");
                await loadAllData();
            } else {
                showToast(data.error || "Authentication failed.");
            }
        } catch (err) {
            console.error("Auth login error", err);
            showToast("Network error authenticating user: " + err.message + "\nAre you viewing this file via file://? Please use http://localhost:8080");
        } finally {
            setBtnLoading(loginBtn, false);
        }
    });

    // Handle Passenger Registration submission
    document.getElementById("auth-passenger-register-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const name = document.getElementById("register-p-name").value.trim();
        const password = document.getElementById("register-p-password").value;

        if (!name || !password) return;

        try {
            const res = await fetch("/api/auth/register-passenger", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name, password })
            });

            const data = await res.json();

            if (res.ok) {
                showToast("Passenger registration successful! You can now log in.");
                document.getElementById("register-p-name").value = "";
                document.getElementById("register-p-password").value = "";
                // Back to login
                loginScreen.style.display = "block";
                passengerRegisterScreen.style.display = "none";
                authTitleText.innerText = "Sign In to DriveNRide";
            } else {
                showToast(data.error || "Registration failed.");
            }
        } catch (err) {
            console.error("Registration error", err);
            showToast("Network error during registration.");
        }
    });

    // Handle Driver Registration submission
    document.getElementById("auth-driver-register-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const name = document.getElementById("register-d-name").value.trim();
        const password = document.getElementById("register-d-password").value;
        const make = document.getElementById("register-d-make").value.trim();
        const model = document.getElementById("register-d-model").value.trim();
        const capacity = document.getElementById("register-d-capacity").value;

        if (!name || !password || !make || !model || !capacity) return;

        try {
            const res = await fetch("/api/auth/register-driver", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name, password, make, model, capacity })
            });

            const data = await res.json();

            if (res.ok) {
                showToast("Driver registration successful! You can now log in.");
                document.getElementById("register-d-name").value = "";
                document.getElementById("register-d-password").value = "";
                document.getElementById("register-d-make").value = "";
                document.getElementById("register-d-model").value = "";
                document.getElementById("register-d-capacity").value = "4";
                // Back to login
                loginScreen.style.display = "block";
                driverRegisterScreen.style.display = "none";
                authTitleText.innerText = "Sign In to DriveNRide";
            } else {
                showToast(data.error || "Registration failed.");
            }
        } catch (err) {
            console.error("Registration error", err);
            showToast("Network error during registration.");
        }
    });
}

// Setup Form Submission Listeners
function setupFormListeners() {
    // Create Trip Offer Form
    document.getElementById("create-trip-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!currentUser || currentUser.role !== "DRIVER") return;
        const driverId = currentUser.id;
        const origin = document.getElementById("trip-origin").value;
        const destination = document.getElementById("trip-dest").value;
        const departureTime = document.getElementById("trip-time").value;
        const maxStops = parseInt(document.getElementById("trip-max-stops").value);
        const maxDetourMinutes = parseInt(document.getElementById("trip-max-detour").value);

        if (new Date(departureTime) < new Date()) {
            showToast("Cannot offer a trip in the past.");
            return;
        }

        try {
            const res = await fetch("/api/trips", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    driver: { id: parseInt(driverId) },
                    origin,
                    destination,
                    departureTime: new Date(departureTime).toISOString(),
                    maxStops,
                    maxDetourMinutes
                })
            });
            if (res.ok) {
                document.getElementById("trip-origin").value = "";
                document.getElementById("trip-dest").value = "";
                document.getElementById("trip-time").value = "";
                logConsole(`[Database] Posted Trip Offer from ${origin} to ${destination}`, "text-indigo");
                await loadAllData();
            } else {
                const errBody = await res.json();
                showToast(errBody.error || "Failed to post trip offer.");
            }
        } catch (err) {
            console.error("Error creating trip", err);
        }
    });

    // Submit Ride Request Form
    document.getElementById("create-ride-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!currentUser || currentUser.role !== "PASSENGER") return;
        const passengerId = currentUser.id;
        const origin = document.getElementById("ride-origin").value;
        const destination = document.getElementById("ride-dest").value;
        const start = document.getElementById("ride-window-start").value;
        const end = document.getElementById("ride-window-end").value;
        const luggageSize = document.getElementById("ride-luggage") ? document.getElementById("ride-luggage").value : "NONE";

        if (new Date(start) < new Date() || new Date(end) < new Date()) {
            showToast("Cannot request a ride in the past.");
            return;
        }

        try {
            const res = await fetch("/api/rides", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    passenger: { id: parseInt(passengerId) },
                    origin,
                    destination,
                    pickupTimeWindowStart: new Date(start).toISOString(),
                    pickupTimeWindowEnd: new Date(end).toISOString(),
                    luggageSize
                })
            });
            if (res.ok) {
                document.getElementById("ride-origin").value = "";
                document.getElementById("ride-dest").value = "";
                document.getElementById("ride-window-start").value = "";
                document.getElementById("ride-window-end").value = "";
                logConsole(`[Database] Submitted Ride Request from ${origin} to ${destination}`, "text-indigo");
                await loadAllData();
            } else {
                const errBody = await res.json();
                showToast(errBody.error || "Failed to submit ride request.");
            }
        } catch (err) {
            console.error("Error creating ride request", err);
        }
    });

    // Submit Passenger Rating Form (Passenger rates Driver)
    const passengerRatingForm = document.getElementById("passenger-create-rating-form");
    if (passengerRatingForm) {
        passengerRatingForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            if (!currentUser) return;
            const reviewerId = currentUser.id;
            const revieweeId = document.getElementById("passenger-rating-reviewee").value;
            const score = parseInt(document.getElementById("passenger-rating-score").value);
            const type = "PASSENGER_RATED";

            if (!revieweeId) return;

            try {
                const res = await fetch("/api/ratings", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        reviewer: { id: parseInt(reviewerId) },
                        reviewee: { id: parseInt(revieweeId) },
                        score,
                        type
                    })
                });
                if (res.ok) {
                    logConsole(`[Ratings] Submitted rating: ${currentUser.name} rated Driver ID ${revieweeId} a score of ${score}`, "text-warning");
                    document.getElementById("passenger-rating-reviewee").value = "";
                    document.getElementById("passenger-rating-score").value = "5";
                    await loadAllData();
                } else {
                    const errBody = await res.json();
                    showToast(errBody.error || "Failed to submit rating review.");
                }
            } catch (err) {
                console.error("Error creating rating", err);
            }
        });
    }

    // Submit Driver Rating Form (Driver rates Passenger)
    const driverRatingForm = document.getElementById("driver-create-rating-form");
    if (driverRatingForm) {
        driverRatingForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            if (!currentUser) return;
            const reviewerId = currentUser.id;
            const revieweeId = document.getElementById("driver-rating-reviewee").value;
            const score = parseInt(document.getElementById("driver-rating-score").value);
            const type = "DRIVER_RATED";

            if (!revieweeId) return;

            try {
                const res = await fetch("/api/ratings", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        reviewer: { id: parseInt(reviewerId) },
                        reviewee: { id: parseInt(revieweeId) },
                        score,
                        type
                    })
                });
                if (res.ok) {
                    logConsole(`[Ratings] Submitted rating: ${currentUser.name} rated Passenger ID ${revieweeId} a score of ${score}`, "text-warning");
                    document.getElementById("driver-rating-reviewee").value = "";
                    document.getElementById("driver-rating-score").value = "5";
                    await loadAllData();
                } else {
                    const errBody = await res.json();
                    showToast(errBody.error || "Failed to submit rating review.");
                }
            } catch (err) {
                console.error("Error creating rating", err);
            }
        });
    }

}

// Setup passenger search listener
function setupPlannerSearchListener() {
    const searchForm = document.getElementById("search-rides-form");
    if (!searchForm) return;

    searchForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const btn = searchForm.querySelector('button[type="submit"]');
        setBtnLoading(btn, true);
        try {
            await runPlannerSearch();
        } finally {
            setBtnLoading(btn, false);
        }
    });
}

// Fetch and load database state
async function loadAllData() {
    try {
        const [usersRes, vehiclesRes, tripsRes, ridesRes, ratingsRes] = await Promise.all([
            fetch("/api/users"),
            fetch("/api/vehicles"),
            fetch("/api/trips"),
            fetch("/api/rides"),
            fetch("/api/ratings")
        ]);

        users = await usersRes.json();
        vehicles = await vehiclesRes.json();
        trips = await tripsRes.json();
        rides = await ridesRes.json();
        ratings = await ratingsRes.json();

        // Render lists & populate select options
        renderPassengersTable();
        renderDriversTable();
        renderTripsTable();
        renderRidesTable();
        renderRatingsTable();
        renderDriverTripsTable();
        renderPassengerRidesTable();
        renderDriverVehicleInfo();
        
        populateDropdowns();
    } catch (err) {
        console.error("Error loading database records", err);
    }
}

function renderDriverTripsTable() {
    const list = document.getElementById("driver-trips-list");
    if (!list || !currentUser) return;

    const myTrips = trips.filter(t => t.driver && t.driver.id === currentUser.id);
    if (myTrips.length === 0) {
        list.innerHTML = `<div class="info-text">You haven't published any trips yet. Offer one — your seats are worth sharing.</div>`;
        return;
    }

    list.innerHTML = myTrips.map(t => {
        const pax = t.passengers || [];
        const hasConfirmed = pax.some(p => (p.status || "PENDING") === "CONFIRMED");
        const pending = pax.filter(p => (p.status || "PENDING") === "PENDING").length;

        const paxRows = pax.length === 0
            ? `<div class="ride-passenger-row text-muted">No bookings yet.</div>`
            : pax.map(p => {
                const pName = p.passenger ? p.passenger.name : "Unknown";
                const pStatus = p.status || "PENDING";
                const controls = pStatus === "PENDING"
                    ? ` <button class="btn btn-sm btn-primary confirm-booking-btn" data-id="${p.id}">Confirm</button>
                        <button class="btn btn-sm btn-danger reject-booking-btn" data-id="${p.id}">Decline</button>`
                    : "";
                return `<div class="ride-passenger-row">
                            <span class="pax-avatar">${pName.charAt(0).toUpperCase()}</span>
                            <span>${pName}</span>
                            ${bookingStatusBadge(pStatus)}
                            ${controls}
                        </div>`;
            }).join("");

        return `
        <div class="ride-card">
            <div class="ride-card-top">
                <div>
                    <div class="ride-route">${t.origin} <span class="route-arrow">→</span> ${t.destination}</div>
                    <div class="ride-meta">
                        <span>🕑 ${formatDateTime(t.departureTime)}</span>
                        <span>⛳ max ${t.maxStops} pickups</span>
                        <span>↺ ${t.maxDetourMinutes} min detour budget</span>
                        ${pending > 0 ? `<span class="text-warning">● ${pending} awaiting your decision</span>` : ""}
                    </div>
                </div>
                <div class="ride-card-actions">
                    ${hasConfirmed ? `<button class="btn btn-sm btn-primary complete-trip-btn" data-id="${t.id}">🏁 Complete</button>` : ""}
                    <button class="btn btn-sm btn-secondary edit-trip-btn" data-id="${t.id}">Edit</button>
                    <button class="btn btn-sm btn-danger delete-trip-btn" data-id="${t.id}">Cancel</button>
                </div>
            </div>
            <div class="ride-passengers">${paxRows}</div>
        </div>`;
    }).join("");

    // listeners
    list.querySelectorAll(".edit-trip-btn").forEach(btn =>
        btn.addEventListener("click", e => openEditTripModal(parseInt(e.currentTarget.getAttribute("data-id")))));
    list.querySelectorAll(".delete-trip-btn").forEach(btn =>
        btn.addEventListener("click", e => deleteTrip(parseInt(e.currentTarget.getAttribute("data-id")))));
    list.querySelectorAll(".confirm-booking-btn").forEach(btn =>
        btn.addEventListener("click", e => bookingTransition(parseInt(e.currentTarget.getAttribute("data-id")), "confirm")));
    list.querySelectorAll(".reject-booking-btn").forEach(btn =>
        btn.addEventListener("click", e => bookingTransition(parseInt(e.currentTarget.getAttribute("data-id")), "reject")));
    list.querySelectorAll(".complete-trip-btn").forEach(btn =>
        btn.addEventListener("click", e => completeTripLifecycle(parseInt(e.currentTarget.getAttribute("data-id")))));
}

function renderPassengerRidesTable() {
    const list = document.getElementById("passenger-rides-list");
    if (!list || !currentUser) return;

    const myRides = rides.filter(r => r.passenger && r.passenger.id === currentUser.id);
    if (myRides.length === 0) {
        list.innerHTML = `<div class="info-text">No rides yet. Search for one, or post a request so drivers can find you.</div>`;
        return;
    }

    list.innerHTML = myRides.map(r => {
        const status = r.status || "PENDING";
        const isActive = (status === "PENDING" || status === "CONFIRMED");
        const actions = isActive
            ? `<button class="btn btn-sm btn-secondary edit-ride-btn" data-id="${r.id}">Edit</button>
               <button class="btn btn-sm btn-danger withdraw-ride-btn" data-id="${r.id}">Withdraw</button>`
            : `<button class="btn btn-sm btn-danger delete-ride-btn" data-id="${r.id}">Remove</button>`;
        return `
        <div class="ride-card">
            <div class="ride-card-top">
                <div>
                    <div class="ride-route">${r.origin} <span class="route-arrow">→</span> ${r.destination} ${bookingStatusBadge(status)}</div>
                    <div class="ride-meta">
                        <span>🕑 pickup ${formatDateTime(r.pickupTimeWindowStart)} – ${formatDateTime(r.pickupTimeWindowEnd)}</span>
                        ${r.luggageSize && r.luggageSize !== "NONE" ? `<span class="luggage-chip">🧳 ${r.luggageSize.toLowerCase()} luggage</span>` : ""}
                    </div>
                </div>
                <div class="ride-card-actions">${actions}</div>
            </div>
        </div>`;
    }).join("");

    list.querySelectorAll(".withdraw-ride-btn").forEach(btn =>
        btn.addEventListener("click", async e => {
            const rideId = parseInt(e.currentTarget.getAttribute("data-id"));
            if (await showConfirm("Withdraw this booking? The driver will be notified.", "Withdraw")) {
                bookingTransition(rideId, "cancel");
            }
        }));
    list.querySelectorAll(".edit-ride-btn").forEach(btn =>
        btn.addEventListener("click", e => openEditRideModal(parseInt(e.currentTarget.getAttribute("data-id")))));
    list.querySelectorAll(".delete-ride-btn").forEach(btn =>
        btn.addEventListener("click", e => deleteRide(parseInt(e.currentTarget.getAttribute("data-id")))));
}

function renderPassengersTable() {
    const tbody = document.querySelector("#passengers-table tbody");
    if (!tbody) return;
    tbody.innerHTML = "";
    
    const passengers = users.filter(u => u.role === 'PASSENGER');
    passengers.forEach(u => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${u.id}</td>
            <td><strong>${u.name}</strong></td>
            <td>⭐ ${u.reputationScore.toFixed(2)}</td>
            <td><span class="badge badge-tier-${getTierClass(u.incentiveTier)}">${u.incentiveTier}</span></td>
        `;
        tbody.appendChild(tr);
    });
}

function renderDriversTable() {
    const tbody = document.querySelector("#drivers-table tbody");
    if (!tbody) return;
    tbody.innerHTML = "";
    
    const drivers = users.filter(u => u.role === 'DRIVER');
    drivers.forEach(u => {
        // Find driver's vehicle
        const vehicle = vehicles.find(v => v.driver && v.driver.id === u.id);
        
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${u.id}</td>
            <td><strong>${u.name}</strong></td>
            <td>⭐ ${u.reputationScore.toFixed(2)}</td>
            <td><span class="badge badge-tier-${getTierClass(u.incentiveTier)}">${u.incentiveTier}</span></td>
            <td>${vehicle ? `${vehicle.make} ${vehicle.model}` : "No Vehicle"}</td>
            <td>👤 ${vehicle ? vehicle.capacity : 0} seats</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderTripsTable() {
    const tbody = document.querySelector("#trips-table tbody");
    if (!tbody) return;
    tbody.innerHTML = "";
    trips.forEach(t => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${t.id}</td>
            <td>${t.driver ? t.driver.name : "N/A"}</td>
            <td><strong>${t.origin} → ${t.destination}</strong></td>
            <td>${formatDateTime(t.departureTime)}</td>
            <td>Max Stops: ${t.maxStops} | Max Extra Time: ${t.maxDetourMinutes} mins</td>
        `;
        tbody.appendChild(tr);
    });
}

async function renderRidesTable() {
    const list = document.getElementById("open-requests-list");
    if (!list) return;

    const now = new Date();
    const assignedRideIds = trips.flatMap(t => t.passengers ? t.passengers.map(req => req.id) : []);
    const activeRides = rides.filter(r => !assignedRideIds.includes(r.id) && new Date(r.pickupTimeWindowStart) >= now);

    if (activeRides.length === 0) {
        list.innerHTML = `<div class="info-text">No open passenger requests right now — check back later.</div>`;
        return;
    }

    // FR-15: rank candidates through the driver's own policy for the next upcoming trip
    let ranked = null;
    let rankTrip = null;
    if (currentUser && currentUser.role === "DRIVER") {
        const myTrips = trips
            .filter(t => t.driver && t.driver.id === currentUser.id && new Date(t.departureTime) >= now)
            .sort((a, b) => new Date(a.departureTime) - new Date(b.departureTime));
        if (myTrips.length > 0) {
            rankTrip = myTrips[0];
            try {
                const res = await fetch(`/api/policies/rank/${currentUser.id}/${rankTrip.id}`);
                if (res.ok) ranked = await res.json();
            } catch (e) {
                console.error("Ranking fetch failed:", e);
            }
        }
    }

    const requestCard = (r, scoreHtml) => {
        const pName = r.passenger ? r.passenger.name : "Unknown";
        const pid = r.passenger ? r.passenger.id : "null";
        const rep = r.passenger && typeof r.passenger.reputationScore === "number"
            ? ` · ⭐ ${r.passenger.reputationScore.toFixed(2)}` : "";
        return `
        <div class="ride-card">
            <div class="ride-card-top">
                <div>
                    <div class="ride-route">
                        <span class="pax-avatar">${pName.charAt(0).toUpperCase()}</span>
                        ${pName} needs: ${r.origin} <span class="route-arrow">→</span> ${r.destination} ${scoreHtml}
                    </div>
                    <div class="ride-meta">
                        <span>🕑 pickup ${formatDateTime(r.pickupTimeWindowStart)} – ${formatDateTime(r.pickupTimeWindowEnd)}</span>
                        <span>${r.luggageSize && r.luggageSize !== "NONE" ? `🧳 ${r.luggageSize.toLowerCase()} luggage` : "🧳 no luggage"}${rep}</span>
                    </div>
                </div>
                <div class="ride-card-actions">
                    <button class="btn btn-sm btn-primary"
                        onclick="offerRideForRequest('${r.origin}', '${r.destination}', ${r.id}, ${pid}, '${r.pickupTimeWindowStart}', '${r.pickupTimeWindowEnd}')">
                        Offer Ride
                    </button>
                </div>
            </div>
        </div>`;
    };

    if (ranked && rankTrip) {
        const filtered = activeRides.length - ranked.length;
        const banner = `
            <div class="rank-banner">
                Ranked by <strong>your travel policy</strong> for trip ${rankTrip.origin} → ${rankTrip.destination}
                — best matches first${filtered > 0 ? ` · <strong>${filtered}</strong> candidate(s) filtered out by your rules` : ""}.
            </div>`;
        if (ranked.length === 0) {
            list.innerHTML = banner + `<div class="info-text">All open requests are blocked by your travel policy.</div>`;
            return;
        }
        list.innerHTML = banner + ranked.map(rc => {
            const bd = Object.entries(rc.breakdown || {}).map(([k, v]) => `${k}: ${v}`).join("  ·  ");
            const chip = `<span class="score-chip" title="${bd}">match ${rc.score}</span>`;
            return requestCard(rc.request, chip);
        }).join("");
        return;
    }

    list.innerHTML = activeRides.map(r => requestCard(r, "")).join("");
}

function renderRatingsTable() {
    const tbody = document.querySelector("#ratings-table-list tbody");
    if (!tbody) return;
    tbody.innerHTML = "";
    ratings.forEach(r => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${r.id}</td>
            <td>${r.reviewer ? r.reviewer.name : `User ${r.reviewerId}`}</td>
            <td>${r.reviewee ? r.reviewee.name : `User ${r.revieweeId}`}</td>
            <td>${"★".repeat(r.score)}${"☆".repeat(5 - r.score)} (${r.score})</td>
            <td><span class="badge ${r.type === 'DRIVER_RATED' ? 'badge-driver' : 'badge-passenger'}">${r.type}</span></td>
        `;
        tbody.appendChild(tr);
    });
}

// Populate rating dropdowns — FR-10 integrity: only counterparts from
// COMPLETED bookings are rateable (backend: /api/bookings/rateable).
async function populateDropdowns() {
    if (!currentUser) return;

    let rateable = [];
    try {
        const res = await fetch(`/api/bookings/rateable/${currentUser.id}`);
        if (res.ok) rateable = await res.json();
    } catch (e) {
        console.error("Failed to load rateable counterparts:", e);
    }

    const fill = (selectId, role, placeholder, emptyHint) => {
        const select = document.getElementById(selectId);
        if (!select) return;
        const previousVal = select.value;
        const options = rateable.filter(u => u.role === role);
        if (options.length === 0) {
            select.innerHTML = `<option value="">${emptyHint}</option>`;
            return;
        }
        select.innerHTML = `<option value="">${placeholder}</option>` +
            options.map(u => `<option value="${u.id}">${u.name} (⭐ ${(u.reputationScore ?? 5).toFixed(2)})</option>`).join("");
        if (previousVal) select.value = previousVal;
    };

    fill("driver-rating-reviewee", "PASSENGER", "Select Passenger...",
         "No rateable passengers yet — complete a trip first");
    fill("passenger-rating-reviewee", "DRIVER", "Select Driver...",
         "No rateable drivers yet — complete a ride first");
}

// Search matching rides using passenger's request parameters
async function runPlannerSearch() {
    const origin = document.getElementById("search-origin").value.trim();
    const destination = document.getElementById("search-dest").value.trim();
    const dateVal = document.getElementById("search-date").value;
    const outputContainer = document.getElementById("planner-output-container");

    if (!currentUser) {
        showToast("You must be logged in to search rides!");
        return;
    }

    outputContainer.innerHTML = `
        <div class="welcome-box">
            <div class="welcome-icon">⚡</div>
            <p>Searching for available trips...</p>
        </div>
    `;

    try {
        const payload = {
            origin: origin,
            destination: destination,
            date: dateVal,
            passengerId: currentUser.id.toString()
        };

        // Prevent searching for past dates
        const searchDateObj = new Date(dateVal);
        const now = new Date();
        now.setHours(0,0,0,0); // reset to midnight for date-only comparison
        
        if (searchDateObj < now) {
            outputContainer.innerHTML = `<div class="info-text" style="background-color: var(--accent-error-bg); color: var(--accent-error);">Search failed: Cannot search for rides in the past.</div>`;
            return;
        }

        const res = await fetch("/api/trips/search-matches", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errText = await res.text();
            outputContainer.innerHTML = `<div class="info-text" style="background-color: var(--accent-error-bg); color: var(--accent-error);">Error searching matches: ${errText}</div>`;
            return;
        }

        const matches = await res.json();
        
        // Log Mapping events to the COTS console
        logConsole(`[Google Maps API] Checked detour for ${origin} → ${destination} for passenger ${currentUser.name}`, "text-indigo");

        if (matches.length === 0) {
            outputContainer.innerHTML = `
                <div class="welcome-box">
                    <div class="welcome-icon">⚠️</div>
                    <p>No matching trips found for this date. Try another date or route.</p>
                </div>
            `;
            const mapEl = document.getElementById("map");
            if (mapEl) mapEl.style.display = "none";
            return;
        }

        renderSearchResults(matches, payload);
    } catch (err) {
        console.error("Error executing route planner search", err);
        outputContainer.innerHTML = `<div class="info-text" style="background-color: var(--accent-error-bg); color: var(--accent-error);">Network error: Failed to reach match search API.</div>`;
    }
}

// Render search results for matching rides
function renderSearchResults(matches, searchPayload) {
    const container = document.getElementById("planner-output-container");
    const mapEl = document.getElementById("map");
    if (mapEl) mapEl.style.display = "none"; 

    let html = `
        <div class="result-header">
            <h3>Available Rides</h3>
            <p style="font-size: 0.78rem; color: var(--text-secondary);">${matches.length} matches found</p>
        </div>
        <div class="matches-list" style="margin-top: 15px;">
    `;

    const now = new Date();
    const futureMatches = matches.filter(m => new Date(m.departureTime) >= now);

    if (futureMatches.length === 0) {
        html += `<div class="info-text">No available rides found for this future date.</div></div>`;
        container.innerHTML = html;
        return;
    }

    futureMatches.forEach((match, idx) => {
        html += `
            <div class="card match-card" style="margin-bottom: 20px; border: 1px solid var(--border-color); padding: 18px; position: relative;">
                <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 12px;">
                    <div>
                        <h4 style="margin: 0; font-size: 1.1rem; color: var(--text-primary);">${match.driverName}</h4>
                        <p style="margin: 2px 0; font-size: 0.78rem; color: var(--text-secondary);">${match.vehicleInfo}</p>
                    </div>
                    <div style="text-align: right;">
                        <span style="font-size: 1.25rem; font-weight: 700; color: var(--accent-success);">$${match.pricing.finalFare.toFixed(2)}</span>
                    </div>
                </div>
                
                <div class="grid-2col-nested" style="font-size: 0.82rem; color: var(--text-secondary); margin-bottom: 15px; border-top: 1px solid var(--border-color); border-bottom: 1px solid var(--border-color); padding: 8px 0;">
                    <div>📅 Departure: <strong>${formatDateTime(match.departureTime)}</strong></div>
                    <div>⏱️ Total Time: <strong>${match.totalTimeMinutes} mins</strong></div>
                    <div>🚗 Total Distance: <strong>${match.totalDistanceKm.toFixed(1)} km</strong></div>
                    <div>👥 Spots Available: <strong>${match.spotsAvailable} seats</strong></div>
                </div>

                <div style="margin-bottom: 15px;">
                    <h5 style="margin: 0 0 6px 0; font-size: 0.85rem; color: var(--text-secondary);">Route Stops:</h5>
                    <div class="mini-timeline" style="font-size: 0.78rem; border-left: 2px dashed var(--border-color); padding-left: 10px; margin-left: 4px;">
        `;

        match.routing.sequence.forEach((stop) => {
            html += `<div style="margin-bottom: 4px; color: var(--text-primary);">${stop}</div>`;
        });

        html += `
                    </div>
                </div>

                <div style="display: flex; gap: 10px;">
                    <button class="btn btn-secondary btn-view-map" data-index="${idx}" style="flex: 1; font-size: 0.82rem; padding: 8px 12px;">🗺️ View on Map</button>
                    <button class="btn btn-primary btn-book-match" data-trip-id="${match.tripOfferId}" style="flex: 1; font-size: 0.82rem; padding: 8px 12px;">Book Ride</button>
                </div>
            </div>
        `;
    });

    html += `</div>`;
    container.innerHTML = html;

    // Attach listeners
    container.querySelectorAll(".btn-view-map").forEach(btn => {
        btn.addEventListener("click", (e) => {
            const index = parseInt(e.currentTarget.getAttribute("data-index"));
            const selectedMatch = matches[index];
            if (mapEl) {
                mapEl.style.display = "block";
                renderRouteMap(selectedMatch.routing.sequence);
                logConsole(`[Google Maps API] Displaying route sequence map for Trip Offer ${selectedMatch.tripOfferId}`, "text-indigo");
            }
        });
    });

    container.querySelectorAll(".btn-book-match").forEach(btn => {
        btn.addEventListener("click", async (e) => {
            const tripId = e.currentTarget.getAttribute("data-trip-id");
            await bookTrip(tripId, searchPayload);
        });
    });
}

// Confirm booking for selected match
async function bookTrip(tripOfferId, searchPayload) {
    const outputContainer = document.getElementById("planner-output-container");
    const mapEl = document.getElementById("map");

    outputContainer.innerHTML = `
        <div class="welcome-box">
            <div class="welcome-icon">⚡</div>
            <p>Processing booking and routing calculations on Stripe & Google Maps systems...</p>
        </div>
    `;
    if (mapEl) mapEl.style.display = "none";

    try {
        const startTime = Date.now();
        const res = await fetch(`/api/trips/${tripOfferId}/book-passenger`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(searchPayload)
        });

        if (!res.ok) {
            const errText = await res.text();
            outputContainer.innerHTML = `<div class="info-text" style="background-color: var(--accent-error-bg); color: var(--accent-error);">Booking failed: ${errText}</div>`;
            return;
        }

        const data = await res.json();
        const durationMs = Date.now() - startTime;

        // Log events to console
        logConsole(`[Google Maps API] Checked direct distance for booking on offer ${tripOfferId}`, "text-indigo");
        logConsole(`[Google Maps API] Formulating detour stop sequences for passenger ${currentUser.name}`, "text-indigo");

        // Log Stripe transaction checks
        if (data.passengers) {
            data.passengers.forEach(p => {
                logConsole(`[Stripe Verification] User ID ${p.passengerId} (${p.passengerName}): Identity verification returned ${p.identityVerified}`, "text-success");
                if (p.paymentCleared) {
                    logConsole(`[Stripe Transactions] Transferred split amount $${p.pricing.finalFare.toFixed(2)} to Driver`, "text-success");
                } else {
                    logConsole(`[Stripe Transactions] FAILED transaction split of $${p.pricing.finalFare.toFixed(2)} from User ${p.passengerId}`, "text-error");
                }
            });
        }

        const selectedTrip = trips.find(t => t.id === parseInt(tripOfferId));
        renderBookingConfirmation(data, selectedTrip, durationMs);

        // Refresh directory data
        await loadAllData();
    } catch (err) {
        console.error("Error confirming booking", err);
        outputContainer.innerHTML = `<div class="info-text" style="background-color: var(--accent-error-bg); color: var(--accent-error);">Network error: Failed to complete booking.</div>`;
    }
}

// Render booking confirmation screen
function renderBookingConfirmation(data, trip, durationMs) {
    const container = document.getElementById("planner-output-container");
    const routing = data.routing;
    
    if (!routing) return;

    let html = `
        <div class="result-header">
            <div>
                <h3>Booking Confirmation Summary</h3>
                <p style="font-size: 0.78rem; color: var(--text-secondary);">Processed in <strong>${durationMs}ms</strong></p>
            </div>
            <span class="result-status-badge ${routing.feasible ? 'status-success' : 'status-failed'}">
                ${routing.feasible ? 'BOOKING CONFIRMED' : 'BOOKING FAILED'}
            </span>
        </div>
    `;

    if (!routing.feasible) {
        html += `
            <div class="card" style="border-color: var(--accent-error); background-color: var(--accent-error-bg); padding: 18px; margin-bottom: 20px;">
                <h4 style="color: var(--accent-error); margin-bottom: 6px;">Ride Booking Unsuccessful</h4>
                <p style="font-size: 0.88rem; color: var(--text-primary);">This ride cannot be accommodated by the selected trip. The driver's route detour limits or vehicle capacity are exceeded. Please try adjusting your locations or search for another ride.</p>
            </div>
        `;
        container.innerHTML = html;
        const mapEl = document.getElementById("map");
        if (mapEl) mapEl.style.display = "none";
        return;
    }

    const mapEl = document.getElementById("map");
    if (mapEl) {
        mapEl.style.display = "block";
        renderRouteMap(routing.sequence);
    }

    html += `
        <div class="result-metrics">
            <div class="metric-item">
                <span class="metric-label">Estimated Time</span>
                <span class="metric-value">${routing.totalTimeMinutes} Minutes</span>
            </div>
            <span style="border-left: 1px solid var(--border-color); height: 35px; align-self: center;"></span>
            <div class="metric-item">
                <span class="metric-label">Trip Distance</span>
                <span class="metric-value">${routing.totalDistanceKm.toFixed(1)} km</span>
            </div>
            <span style="border-left: 1px solid var(--border-color); height: 35px; align-self: center;"></span>
            <div class="metric-item">
                <span class="metric-label">Booking Status</span>
                <span class="metric-value" style="color: var(--accent-success);">${data.bookingStatus}</span>
            </div>
        </div>
        
        <div class="grid-2col-nested" style="margin-top: 24px; align-items: start;">
            <div>
                <h4 style="font-size: 0.95rem; color: var(--text-secondary); margin-bottom: 12px;">Optimal Stopping Timeline</h4>
                <div class="timeline-container">
    `;

    routing.sequence.forEach((stop, index) => {
        let stepClass = "";
        let stepDesc = "";
        
        if (index === 0) {
            stepClass = "origin";
            stepDesc = "Driver starts trip";
        } else if (index === routing.sequence.length - 1) {
            stepClass = "destination";
            stepDesc = "Driver arrives at destination";
        } else if (stop.startsWith("PICKUP")) {
            stepClass = "pickup";
            stepDesc = "Passenger pickup stop";
        } else if (stop.startsWith("DROPOFF")) {
            stepClass = "dropoff";
            stepDesc = "Passenger dropoff destination";
        }

        html += `
            <div class="timeline-step ${stepClass}">
                <div class="step-title">${stop}</div>
                <div class="step-desc">${stepDesc}</div>
            </div>
        `;
    });

    html += `
                </div>
            </div>
            
            <div class="passengers-pricing-breakdown" style="margin-top: 0;">
                <h4 style="font-size: 0.95rem; color: var(--text-secondary); margin-bottom: 12px;">Fares & Policy Breakdown</h4>
    `;

    if (data.passengers && data.passengers.length > 0) {
        data.passengers.forEach(p => {
            html += `
                <div class="passenger-pricing-card">
                    <div class="pricing-card-header">
                        <span class="pricing-card-name">${p.passengerName}</span>
                        <span class="pricing-card-fare">$${p.pricing.finalFare.toFixed(2)}</span>
                    </div>
                    <div class="pricing-details-grid">
                        <div>Base Fare:</div>
                        <div style="text-align: right;">$${p.pricing.baseFare.toFixed(2)}</div>
                        <div>Reputation:</div>
                        <div style="text-align: right;">⭐ ${p.reputationScore.toFixed(2)} (${p.incentiveTier})</div>
                        <div>COTS Stripe:</div>
                        <div style="text-align: right; color: ${p.paymentCleared ? 'var(--accent-success)' : 'var(--accent-error)'}; font-weight: 600;">
                            ${p.paymentCleared ? 'Paid' : 'Hold'}
                        </div>
                    </div>
            `;

            if (p.pricing.appliedPolicies && p.pricing.appliedPolicies.length > 0) {
                html += `<div class="applied-policy-list">`;
                p.pricing.appliedPolicies.forEach(pol => {
                    html += `<span class="policy-badge">${pol}</span>`;
                });
                html += `</div>`;
            } else {
                html += `<div style="font-size: 0.72rem; color: var(--text-muted); margin-top: 10px; font-style: italic;">No surcharges or discounts applied.</div>`;
            }

            html += `</div>`;
        });
    } else {
        html += `<p class="placeholder-text">No passenger request details returned.</p>`;
    }

    html += `
            </div>
        </div>
    `;

    container.innerHTML = html;
}

// Render planner results
function renderPlannerOutput(data, trip, durationMs) {
    const container = document.getElementById("planner-output-container");
    const routing = data.routing;
    
    if (!routing) return;

    let html = `
        <div class="result-header">
            <div>
                <h3>Matching Results Summary</h3>
                <p style="font-size: 0.78rem; color: var(--text-secondary);">Algorithm computed in <strong>${durationMs}ms</strong></p>
            </div>
            <span class="result-status-badge ${routing.feasible ? 'status-success' : 'status-failed'}">
                ${routing.feasible ? 'FEASIBLE ROUTE FOUND' : 'INFEASIBLE ROUTE'}
            </span>
        </div>
    `;

    if (!routing.feasible) {
        html += `
            <div class="card" style="border-color: var(--accent-error); background-color: var(--accent-error-bg); padding: 18px; margin-bottom: 20px;">
                <h4 style="color: var(--accent-error); margin-bottom: 6px;">No Matching Ride Found</h4>
                <p style="font-size: 0.88rem; color: var(--text-primary);">We couldn't find a driver trip that can accommodate this ride. This could be because the detour is too long for the driver, or the vehicle is full. Please try adjusting your pickup time or locations.</p>
            </div>
        `;
        container.innerHTML = html;
        const mapEl = document.getElementById("map");
        if (mapEl) mapEl.style.display = "none";
        return;
    }

    // Show map container
    const mapEl = document.getElementById("map");
    if (mapEl) {
        mapEl.style.display = "block";
        renderRouteMap(routing.sequence);
    }

    // Success layout metrics
    html += `
        <div class="result-metrics">
            <div class="metric-item">
                <span class="metric-label">Estimated Time</span>
                <span class="metric-value">${routing.totalTimeMinutes} Minutes</span>
            </div>
            <span style="border-left: 1px solid var(--border-color); height: 35px; align-self: center;"></span>
            <div class="metric-item">
                <span class="metric-label">Trip Distance</span>
                <span class="metric-value">${routing.totalDistanceKm.toFixed(1)} km</span>
            </div>
            <span style="border-left: 1px solid var(--border-color); height: 35px; align-self: center;"></span>
            <div class="metric-item">
                <span class="metric-label">Status</span>
                <span class="metric-value" style="color: var(--accent-success);">${data.bookingStatus}</span>
            </div>
        </div>
        
        <div class="grid-2col-nested" style="margin-top: 24px; align-items: start;">
            <!-- Timeline Stops -->
            <div>
                <h4 style="font-size: 0.95rem; color: var(--text-secondary); margin-bottom: 12px;">Optimal Stopping Timeline</h4>
                <div class="timeline-container">
    `;

    routing.sequence.forEach((stop, index) => {
        let stepClass = "";
        let stepDesc = "";
        
        if (index === 0) {
            stepClass = "origin";
            stepDesc = "Driver starts trip";
        } else if (index === routing.sequence.length - 1) {
            stepClass = "destination";
            stepDesc = "Driver arrives at destination";
        } else if (stop.startsWith("PICKUP")) {
            stepClass = "pickup";
            stepDesc = "Passenger pickup stop";
        } else if (stop.startsWith("DROPOFF")) {
            stepClass = "dropoff";
            stepDesc = "Passenger dropoff destination";
        }

        html += `
            <div class="timeline-step ${stepClass}">
                <div class="step-title">${stop}</div>
                <div class="step-desc">${stepDesc}</div>
            </div>
        `;
    });

    html += `
                </div>
            </div>
            
            <!-- Pricing breakdown cards -->
            <div class="passengers-pricing-breakdown" style="margin-top: 0;">
                <h4 style="font-size: 0.95rem; color: var(--text-secondary); margin-bottom: 12px;">Fares & Policy Breakdown</h4>
    `;

    if (data.passengers && data.passengers.length > 0) {
        data.passengers.forEach(p => {
            html += `
                <div class="passenger-pricing-card">
                    <div class="pricing-card-header">
                        <span class="pricing-card-name">${p.passengerName}</span>
                        <span class="pricing-card-fare">$${p.pricing.finalFare.toFixed(2)}</span>
                    </div>
                    <div class="pricing-details-grid">
                        <div>Base Fare:</div>
                        <div style="text-align: right;">$${p.pricing.baseFare.toFixed(2)}</div>
                        <div>Reputation:</div>
                        <div style="text-align: right;">⭐ ${p.reputationScore.toFixed(2)} (${p.incentiveTier})</div>
                        <div>COTS Stripe:</div>
                        <div style="text-align: right; color: ${p.paymentCleared ? 'var(--accent-success)' : 'var(--accent-error)'}; font-weight: 600;">
                            ${p.paymentCleared ? 'Paid' : 'Hold'}
                        </div>
                    </div>
            `;

            if (p.pricing.appliedPolicies && p.pricing.appliedPolicies.length > 0) {
                html += `<div class="applied-policy-list">`;
                p.pricing.appliedPolicies.forEach(pol => {
                    html += `<span class="policy-badge">${pol}</span>`;
                });
                html += `</div>`;
            } else {
                html += `<div style="font-size: 0.72rem; color: var(--text-muted); margin-top: 10px; font-style: italic;">No surcharges or discounts applied.</div>`;
            }

            html += `</div>`;
        });
    } else {
        html += `<p class="placeholder-text">No passenger requests were included in this route.</p>`;
    }

    html += `
            </div>
        </div>
    `;

    container.innerHTML = html;
}

// Visual console logs updater
function logConsole(message, cssClass = "") {
    const feed = document.getElementById("console-logs-feed");
    const timestamp = new Date().toLocaleTimeString();
    const line = document.createElement("div");
    line.className = `console-line ${cssClass}`;
    line.innerText = `[${timestamp}] ${message}`;
    feed.appendChild(line);
    
    // Auto scroll console
    feed.scrollTop = feed.scrollHeight;
}

// Utility formatting functions
function getTierClass(tier) {
    if (tier === "PREMIUM_PRICING") return "premium";
    return tier.toLowerCase();
}

function formatDateTime(isoString) {
    if (!isoString) return "";
    const date = new Date(isoString);
    return date.toLocaleDateString() + " " + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// Google Maps rendering functions
function initGoogleMap() {
    if (map) return;
    try {
        const mapOptions = {
            zoom: 7,
            center: { lat: 40.7128, lng: -74.0060 }, // Default to NYC area
            styles: [
                {
                    "featureType": "all",
                    "elementType": "labels.text.fill",
                    "stylers": [{"color": "#ffffff"}]
                },
                {
                    "featureType": "all",
                    "elementType": "labels.text.stroke",
                    "stylers": [{"color": "#000000"}, {"lightness": 13}]
                },
                {
                    "featureType": "administrative",
                    "elementType": "geometry.fill",
                    "stylers": [{"color": "#000000"}, {"lightness": 20}]
                },
                {
                    "featureType": "landscape",
                    "elementType": "geometry",
                    "stylers": [{"color": "#121822"}]
                },
                {
                    "featureType": "poi",
                    "elementType": "geometry",
                    "stylers": [{"color": "#1a2232"}]
                },
                {
                    "featureType": "road.highway",
                    "elementType": "geometry.fill",
                    "stylers": [{"color": "#303f56"}]
                },
                {
                    "featureType": "road.highway",
                    "elementType": "geometry.stroke",
                    "stylers": [{"color": "#1e293b"}]
                },
                {
                    "featureType": "water",
                    "elementType": "geometry",
                    "stylers": [{"color": "#090d16"}]
                }
            ]
        };
        map = new google.maps.Map(document.getElementById("map"), mapOptions);
        directionsService = new google.maps.DirectionsService();
        directionsRenderer = new google.maps.DirectionsRenderer({
            map: map,
            suppressMarkers: false
        });
    } catch (err) {
        console.error("Failed to initialize Google Map", err);
    }
}

let googleMapsAuthFailed = false;

window.gm_authFailure = () => {
    googleMapsAuthFailed = true;
    logConsole("[Google Maps] API Key authorization failed. Maps JavaScript API might not be enabled on your GCP Console. Using visual route sequence fallback.", "text-warning");
    // If the map is displayed, redraw using fallback visualization
    const mapEl = document.getElementById("map");
    if (mapEl && mapEl.style.display !== "none") {
        const container = document.getElementById("planner-output-container");
        // Look for routing sequence displayed on the screen to redraw
        const miniTimeline = container.querySelector(".mini-timeline");
        if (miniTimeline) {
            const sequence = Array.from(miniTimeline.children).map(div => div.innerText);
            showFallbackRouteVisualization(sequence);
        } else {
            const timelineSteps = container.querySelectorAll(".step-title");
            if (timelineSteps.length > 0) {
                const sequence = Array.from(timelineSteps).map(div => div.innerText);
                showFallbackRouteVisualization(sequence);
            } else {
                showFallbackRouteVisualization(null);
            }
        }
    }
};

function showFallbackRouteVisualization(sequence) {
    const mapEl = document.getElementById("map");
    if (!mapEl) return;

    if (!sequence || sequence.length === 0) {
        mapEl.innerHTML = `
            <div style="padding: 24px; text-align: center; color: var(--text-secondary); background: rgba(255,255,255,0.02); height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; border-radius: var(--radius-md); box-sizing: border-box; height: 380px;">
                <div style="font-size: 2rem; margin-bottom: 12px;">🗺️</div>
                <h4>Maps JavaScript API Offline</h4>
                <p style="font-size: 0.8rem; max-width: 320px; margin: 8px 0 0 0; line-height: 1.4; color: var(--text-muted);">
                    Google Maps API returned auth failure. Please ensure **Maps JavaScript API** is enabled for your key in the GCP Console.
                </p>
            </div>
        `;
        return;
    }

    const stops = sequence.map((s, index) => {
        if (s.startsWith("Origin: ")) {
            return { name: s.replace("Origin: ", "").trim(), type: "origin", label: "Origin" };
        } else if (s.startsWith("Destination: ")) {
            return { name: s.replace("Destination: ", "").trim(), type: "destination", label: "Destination" };
        } else if (s.includes(" at ")) {
            const parts = s.split(" at ");
            const typeStr = parts[0].toLowerCase().includes("pickup") ? "pickup" : "dropoff";
            return { name: parts[1].trim(), type: typeStr, label: parts[0].trim() };
        }
        return { name: s, type: "stop", label: "Stop " + (index + 1) };
    });
    let html = `
        <div style="padding: 24px; color: var(--text-primary); background: rgba(18,24,34,0.7); display: flex; flex-direction: column; justify-content: center; align-items: center; border-radius: var(--radius-md); box-sizing: border-box; border: 1px solid var(--border-color); height: 380px;">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 24px; width: 100%; justify-content: center;">
                <span style="font-size: 1.2rem;">🧭</span>
                <h4 style="margin: 0; font-size: 0.95rem; letter-spacing: 0.5px; text-transform: uppercase; color: var(--text-secondary);">Trip Stops & Route</h4>
            </div>
            
            <div style="display: flex; align-items: center; gap: 10px; width: 100%; overflow-x: auto; padding: 15px 5px; justify-content: center; box-sizing: border-box;">
    `;

    stops.forEach((stop, index) => {
        let badgeColor = "var(--text-secondary)";
        let icon = "📍";
        
        if (stop.type === "origin") {
            badgeColor = "var(--accent-indigo)";
            icon = "🚗";
        } else if (stop.type === "destination") {
            badgeColor = "var(--accent-success)";
            icon = "🏁";
        } else if (stop.type === "pickup") {
            badgeColor = "var(--accent-warning)";
            icon = "👤";
        } else if (stop.type === "dropoff") {
            badgeColor = "var(--accent-error)";
            icon = "⬇️";
        }

        html += `
            <div style="display: flex; flex-direction: column; align-items: center; text-align: center; min-width: 90px; max-width: 110px;">
                <div style="width: 44px; height: 44px; border-radius: 50%; background: ${badgeColor}; display: flex; justify-content: center; align-items: center; font-size: 1.20rem; box-shadow: 0 4px 10px rgba(0,0,0,0.3); margin-bottom: 8px; border: 2px solid rgba(255,255,255,0.1);">
                    ${icon}
                </div>
                <div style="font-size: 0.76rem; font-weight: 600; color: var(--text-primary); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; width: 100%;">
                    ${stop.name}
                </div>
                <div style="font-size: 0.65rem; color: var(--text-muted); margin-top: 2px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; width: 100%;">
                    ${stop.label}
                </div>
            </div>
        `;

        if (index < stops.length - 1) {
            html += `
                <div style="display: flex; align-items: center; justify-content: center; min-width: 30px; color: var(--text-secondary); font-size: 1.1rem; opacity: 0.7;">
                    ➔
                </div>
            `;
        }
    });

    html += `
            </div>
            
            <p style="font-size: 0.72rem; color: var(--text-muted); text-align: center; margin: 24px 0 0 0; max-width: 440px; line-height: 1.4;">
                ⚠️ Google Maps unavailable. Set the GOOGLE_MAPS_API_KEY environment variable (with Maps JavaScript API enabled) to view street maps — this fallback visualization works without it.
            </p>
        </div>
    `;

    mapEl.innerHTML = html;
}

function renderRouteMap(sequence) {
    if (!sequence || sequence.length < 2) return;
    
    // Use visual route sequence fallback if API key failed authentication
    if (googleMapsAuthFailed || typeof google === "undefined" || !google.maps) {
        showFallbackRouteVisualization(sequence);
        return;
    }
    
    initGoogleMap();
    
    if (googleMapsAuthFailed) {
        showFallbackRouteVisualization(sequence);
        return;
    }
    
    // Parse stop names from labels
    const stops = sequence.map(s => {
        if (s.startsWith("Origin: ")) {
            return s.replace("Origin: ", "").trim();
        } else if (s.startsWith("Destination: ")) {
            return s.replace("Destination: ", "").trim();
        } else if (s.includes(" at ")) {
            return s.substring(s.lastIndexOf(" at ") + 4).trim();
        }
        return s;
    });

    if (directionsService && directionsRenderer) {
        const originAddr = stops[0];
        const destAddr = stops[stops.length - 1];
        const waypoints = stops.slice(1, stops.length - 1).map(w => ({
            location: w,
            stopover: true
        }));

        const request = {
            origin: originAddr,
            destination: destAddr,
            waypoints: waypoints,
            travelMode: google.maps.TravelMode.DRIVING
        };

        directionsService.route(request, (result, status) => {
            if (status === 'OK') {
                directionsRenderer.setDirections(result);
            } else {
                console.error("Google Maps Directions API returned: " + status);
                showFallbackRouteVisualization(sequence);
            }
        });
    }
}

// Helper to format ISO date to datetime-local format
function toDatetimeLocal(isoString) {
    if (!isoString) return "";
    const date = new Date(isoString);
    const ten = function (i) {
        return (i < 10 ? '0' : '') + i;
    };
    const YYYY = date.getFullYear();
    const MM = ten(date.getMonth() + 1);
    const DD = ten(date.getDate());
    const HH = ten(date.getHours());
    const MIN = ten(date.getMinutes());
    return `${YYYY}-${MM}-${DD}T${HH}:${MIN}`;
}

// Render driver vehicle details read-only
function renderDriverVehicleInfo() {
    const displayEl = document.getElementById("driver-vehicle-info");
    if (!displayEl) return;
    if (!currentUser || currentUser.role !== "DRIVER") return;

    // Find vehicle registered to driver
    const vehicle = vehicles.find(v => v.driver && v.driver.id === currentUser.id);
    if (vehicle) {
        displayEl.innerHTML = `🚗 <strong>Registered Vehicle:</strong> ${vehicle.make} ${vehicle.model} (${vehicle.capacity} seats max)`;
    } else {
        displayEl.innerHTML = `⚠️ <strong>No vehicle registered.</strong> Please contact administration.`;
    }
}

// Edit and Delete handlers for Trips
function openEditTripModal(tripId) {
    const trip = trips.find(t => t.id === tripId);
    if (!trip) return;

    document.getElementById("edit-trip-id").value = tripId;
    document.getElementById("edit-trip-origin").value = trip.origin;
    document.getElementById("edit-trip-dest").value = trip.destination;
    document.getElementById("edit-trip-time").value = toDatetimeLocal(trip.departureTime);
    document.getElementById("edit-trip-max-stops").value = trip.maxStops;
    document.getElementById("edit-trip-max-detour").value = trip.maxDetourMinutes;

    document.getElementById("edit-trip-modal").style.display = "flex";
}

async function deleteTrip(tripId) {
    if (!(await showConfirm("Are you sure you want to cancel this ride offer?", "Cancel Offer", "Keep"))) return;

    try {
        const res = await fetch(`/api/trips/${tripId}`, {
            method: "DELETE"
        });
        if (res.ok) {
            logConsole(`[Database] Cancelled Trip Offer ID: ${tripId}`, "text-warning");
            await loadAllData();
        } else {
            showToast("Failed to cancel trip.");
        }
    } catch (err) {
        console.error("Error cancelling trip", err);
    }
}

// Edit and Delete handlers for Ride Requests
function openEditRideModal(rideId) {
    const ride = rides.find(r => r.id === rideId);
    if (!ride) return;

    document.getElementById("edit-ride-id").value = rideId;
    document.getElementById("edit-ride-origin").value = ride.origin;
    document.getElementById("edit-ride-dest").value = ride.destination;
    document.getElementById("edit-ride-window-start").value = toDatetimeLocal(ride.pickupTimeWindowStart);
    document.getElementById("edit-ride-window-end").value = toDatetimeLocal(ride.pickupTimeWindowEnd);

    document.getElementById("edit-ride-modal").style.display = "flex";
}

async function deleteRide(rideId) {
    if (!(await showConfirm("Are you sure you want to cancel this booking/request?", "Cancel It", "Keep"))) return;

    try {
        const res = await fetch(`/api/rides/${rideId}`, {
            method: "DELETE"
        });
        if (res.ok) {
            logConsole(`[Database] Cancelled Ride Booking/Request ID: ${rideId}`, "text-warning");
            await loadAllData();
        } else {
            showToast("Failed to cancel booking/request.");
        }
    } catch (err) {
        console.error("Error cancelling booking/request", err);
    }
}

// Modal Listeners
function setupEditModalListeners() {
    // Close modals on cancel
    document.getElementById("btn-cancel-edit-trip").addEventListener("click", () => {
        document.getElementById("edit-trip-modal").style.display = "none";
    });

    document.getElementById("btn-cancel-edit-ride").addEventListener("click", () => {
        document.getElementById("edit-ride-modal").style.display = "none";
    });

    // Close on click outside modal card
    document.querySelectorAll(".modal-overlay").forEach(overlay => {
        overlay.addEventListener("click", (e) => {
            if (e.target === overlay) {
                overlay.style.display = "none";
            }
        });
    });

    // Edit Trip Form Submit
    document.getElementById("edit-trip-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const tripId = parseInt(document.getElementById("edit-trip-id").value);
        const origin = document.getElementById("edit-trip-origin").value;
        const destination = document.getElementById("edit-trip-dest").value;
        const departureTime = document.getElementById("edit-trip-time").value;
        const maxStops = parseInt(document.getElementById("edit-trip-max-stops").value);
        const maxDetourMinutes = parseInt(document.getElementById("edit-trip-max-detour").value);

        try {
            const res = await fetch(`/api/trips/${tripId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    driver: { id: currentUser.id },
                    origin,
                    destination,
                    departureTime: new Date(departureTime).toISOString(),
                    maxStops,
                    maxDetourMinutes
                })
            });

            if (res.ok) {
                document.getElementById("edit-trip-modal").style.display = "none";
                logConsole(`[Database] Updated Trip Offer ID: ${tripId}`, "text-indigo");
                await loadAllData();
            } else {
                const errorText = await res.text();
                showToast(`Failed to update trip offer.\n${errorText}`);
            }
        } catch (err) {
            console.error("Error updating trip", err);
        }
    });

    // Edit Ride Request Form Submit
    document.getElementById("edit-ride-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        const rideId = parseInt(document.getElementById("edit-ride-id").value);
        const origin = document.getElementById("edit-ride-origin").value;
        const destination = document.getElementById("edit-ride-dest").value;
        const start = document.getElementById("edit-ride-window-start").value;
        const end = document.getElementById("edit-ride-window-end").value;

        try {
            const res = await fetch(`/api/rides/${rideId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    passenger: { id: currentUser.id },
                    origin,
                    destination,
                    pickupTimeWindowStart: new Date(start).toISOString(),
                    pickupTimeWindowEnd: new Date(end).toISOString()
                })
            });

            if (res.ok) {
                document.getElementById("edit-ride-modal").style.display = "none";
                logConsole(`[Database] Updated Ride Request ID: ${rideId}`, "text-indigo");
                await loadAllData();
            } else {
                showToast("Failed to update ride request.");
            }
        } catch (err) {
            console.error("Error updating ride request", err);
        }
    });
}

// SETUP PROFILE PAGE EVENT LISTENERS
function setupProfileListeners() {
    const profileCard = document.querySelector(".session-profile-card");
    if (profileCard) {
        profileCard.addEventListener("click", (e) => {
            if (e.target.id === "btn-logout" || e.target.closest("#btn-logout")) {
                return;
            }
            openProfileDashboard();
        });
    }

    const btnBack = document.getElementById("btn-profile-back");
    if (btnBack) {
        btnBack.addEventListener("click", () => {
            updateDashboardVisibility();
        });
    }

    const btnDelete = document.getElementById("btn-profile-delete");
    if (btnDelete) {
        btnDelete.addEventListener("click", async () => {
            if (!currentUser) return;
            const confirmed = await showConfirm("Are you sure you want to delete your account? This action cannot be undone and will delete all your trips, bookings, and ratings.");
            if (!confirmed) return;

            try {
                const res = await fetch(`/api/users/${currentUser.id}`, {
                    method: "DELETE"
                });

                if (res.ok) {
                    showToast("Your account has been deleted successfully.");
                    localStorage.removeItem("routeshare_user");
                    showAuthScreens();
                    logConsole("[Session] Account deleted and logged out.", "text-warning");
                } else {
                    showToast("Failed to delete account. Please try again.");
                }
            } catch (err) {
                console.error("Error deleting account", err);
                showToast("Network error deleting account.");
            }
        });
    }

    const profileForm = document.getElementById("profile-form");
    if (profileForm) {
        profileForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            if (!currentUser) return;

            const name = document.getElementById("profile-username").value.trim();
            const password = document.getElementById("profile-password").value;

            if (!name || !password) {
                showToast("Username and password are required.");
                return;
            }

            try {
                const userRes = await fetch(`/api/users/${currentUser.id}`);
                if (!userRes.ok) throw new Error("Failed to fetch user details to update.");
                const userDetails = await userRes.json();

                userDetails.name = name;
                userDetails.password = password;

                const res = await fetch(`/api/users/${currentUser.id}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(userDetails)
                });

                if (!res.ok) {
                    showToast("Failed to update profile settings.");
                    return;
                }

                const updatedUser = await res.json();

                if (currentUser.role === "DRIVER") {
                    const vehicleId = document.getElementById("profile-vehicle-id").value;
                    const make = document.getElementById("profile-car-make").value.trim();
                    const model = document.getElementById("profile-car-model").value.trim();
                    const capacity = parseInt(document.getElementById("profile-car-capacity").value);

                    if (!make || !model || isNaN(capacity)) {
                        showToast("Vehicle make, model, and capacity are required for drivers.");
                        return;
                    }

                    const vehiclePayload = {
                        driver: { id: currentUser.id },
                        make: make,
                        model: model,
                        capacity: capacity
                    };

                    const vRes = await fetch(`/api/vehicles/${vehicleId}`, {
                        method: "PUT",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(vehiclePayload)
                    });

                    if (!vRes.ok) {
                        showToast("User details updated, but failed to update vehicle details.");
                        return;
                    }
                }

                const sessionUser = {
                    id: updatedUser.id,
                    name: updatedUser.name,
                    role: updatedUser.role,
                    reputationScore: updatedUser.reputationScore,
                    incentiveTier: updatedUser.incentiveTier
                };
                localStorage.setItem("routeshare_user", JSON.stringify(sessionUser));
                currentUser = sessionUser;

                updateSessionDisplay();
                await loadAllData();
                updateDashboardVisibility();
                showToast("Profile details updated successfully.");
                logConsole(`[Profile] Updated details for User ID: ${currentUser.id}`, "text-success");
            } catch (err) {
                console.error("Error updating profile", err);
                showToast("Network error updating profile.");
            }
        });
    }
}

async function openProfileDashboard() {
    navigateTo("view-profile");

    if (!currentUser) return;
    try {
        const res = await fetch(`/api/users/${currentUser.id}`);
        if (!res.ok) throw new Error("Failed to fetch profile details");
        const userDetails = await res.json();
        
        
        
        
        
        
        
        
        
        document.getElementById("profile-username").value = userDetails.name;
        document.getElementById("profile-password").value = ""; // hashes are never sent by the API
        document.getElementById("profile-role").value = userDetails.role;

        // FR-15/FR-16: driver-owned policy engine management
        const policySection = document.getElementById("profile-policy-section");
        if (policySection) {
            const isDriver = userDetails.role === "DRIVER";
            policySection.style.display = isDriver ? "block" : "none";
            if (isDriver) loadDriverPolicies();
        }
        
        const vehicleSection = document.getElementById("profile-vehicle-section");
        if (userDetails.role === "DRIVER") {
            vehicleSection.style.display = "block";
            const vRes = await fetch(`/api/vehicles/driver/${currentUser.id}`);
            if (vRes.ok) {
                const vehiclesList = await vRes.json();
                if (vehiclesList.length > 0) {
                    const vehicle = vehiclesList[0];
                    document.getElementById("profile-vehicle-id").value = vehicle.id;
                    document.getElementById("profile-car-make").value = vehicle.make;
                    document.getElementById("profile-car-model").value = vehicle.model;
                    document.getElementById("profile-car-capacity").value = vehicle.capacity;
                } else {
                    document.getElementById("profile-vehicle-id").value = "";
                    document.getElementById("profile-car-make").value = "";
                    document.getElementById("profile-car-model").value = "";
                    document.getElementById("profile-car-capacity").value = "4";
                }
            }
        } else {
            vehicleSection.style.display = "none";
        }
        
        logConsole("[Profile] Loaded profile settings.", "text-info");
    } catch (error) {
        console.error(error);
        logConsole(`[Profile Error] ${error.message}`, "text-danger");
    }
}

async function offerRideForRequest(origin, destination, requestId, passengerId, windowStart, windowEnd) {
    if (currentUser && currentUser.role === 'DRIVER' && requestId && passengerId) {
        try {
            const response = await fetch('/api/trips/check-existing-matches', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    driverId: currentUser.id.toString(),
                    rideRequestId: requestId.toString()
                })
            });
            
            if (response.ok) {
                const matches = await response.json();
                if (matches && matches.length > 0) {
                    let msg = `Good news! You have ${matches.length} existing trip(s) that can accommodate this passenger.\n\n`;
                    matches.forEach((m, idx) => {
                        msg += `${idx + 1}. Trip #${m.tripOfferId}: ${m.origin} to ${m.destination}\n`;
                    });
                    msg += `\nWould you like to add the passenger to the first matching trip (Trip #${matches[0].tripOfferId})?\nClick OK to add, or Cancel to create a brand new trip.`;
                    
                    if (await showConfirm(msg, "Add to Trip", "New Trip")) {
                        await bindPassengerToExistingTrip(matches[0].tripOfferId, origin, destination, passengerId, windowStart, windowEnd);
                        return;
                    }
                }
            }
        } catch (e) {
            console.error("Failed to check existing matches", e);
        }
    }

    navigateTo('view-offer-trip');
    document.getElementById('trip-origin').value = origin;
    document.getElementById('trip-dest').value = destination;
    document.getElementById('trip-time').focus();
    logConsole(`[System] Pre-filled Trip Offer form for passenger request: ${origin} to ${destination}`, "text-info");
}

async function bindPassengerToExistingTrip(tripOfferId, origin, destination, passengerId, windowStart, windowEnd) {
    try {
        const res = await fetch(`/api/trips/${tripOfferId}/book-passenger`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                passengerId: passengerId.toString(),
                origin: origin,
                destination: destination,
                pickupTimeWindowStart: windowStart,
                pickupTimeWindowEnd: windowEnd
            })
        });
        
        if (res.ok) {
            const data = await res.json();
            if (data.bookingStatus === 'FAILED_ROUTING') {
                showToast("Could not route the passenger on this trip.");
            } else {
                showToast("Successfully added the passenger to your existing trip!");
                logConsole(`[System] Passenger added to Trip #${tripOfferId}`, "text-success");
                await loadAllData();
                navigateTo('view-my-trips');
            }
        } else {
            const text = await res.text();
            showToast("Error binding passenger: " + text);
        }
    } catch (e) {
        console.error("Error linking trip:", e);
        showToast("An error occurred while linking the trip.");
    }
}

/* ═══════════════════════════════════════════════════════════════════
   NOTIFICATION CENTER (Sprint 7: FR-13) — Observer pattern client side
   Polls the inbox endpoint and renders the bell badge + dropdown panel.
   ═══════════════════════════════════════════════════════════════════ */
let notifPollTimer = null;
let cachedNotifications = [];

function startNotificationPolling() {
    if (notifPollTimer) return; // idempotent
    loadNotifications();
    notifPollTimer = setInterval(loadNotifications, 30000);
}

function stopNotificationPolling() {
    if (notifPollTimer) {
        clearInterval(notifPollTimer);
        notifPollTimer = null;
    }
    cachedNotifications = [];
}

async function loadNotifications() {
    if (!currentUser) return;
    try {
        const res = await fetch(`/api/notifications/user/${currentUser.id}`);
        if (!res.ok) return;
        cachedNotifications = await res.json();
        renderNotificationBadge();
        renderNotificationPanel();
    } catch (e) {
        console.error("Notification polling failed:", e);
    }
}

function renderNotificationBadge() {
    const badge = document.getElementById("notif-badge");
    if (!badge) return;
    const unread = cachedNotifications.filter(n => !n.read).length;
    badge.style.display = unread > 0 ? "inline-block" : "none";
    badge.innerText = unread > 9 ? "9+" : unread;
}

function renderNotificationPanel() {
    const list = document.getElementById("notif-list");
    if (!list) return;
    if (cachedNotifications.length === 0) {
        list.innerHTML = `<p class="text-muted" style="padding: 12px;">No notifications yet.</p>`;
        return;
    }
    list.innerHTML = cachedNotifications.map(n => `
        <div class="notif-item ${n.read ? '' : 'unread'}" onclick="openNotification(${n.id}, '${n.type}')" title="Open related view">
            ${n.type === 'RATING' ? '⭐' : n.type === 'BOOKING' ? '🚗' : 'ℹ️'} ${n.message}
            <span class="notif-time">${relativeTime(n.createdAt)}</span>
        </div>
    `).join("");
}

function toggleNotificationPanel() {
    const panel = document.getElementById("notif-panel");
    if (!panel) return;
    panel.style.display = panel.style.display === "none" ? "block" : "none";
    if (panel.style.display === "block") loadNotifications();
}

async function markNotifRead(id) {
    try {
        await fetch(`/api/notifications/${id}/mark-read`, { method: "POST" });
        await loadNotifications();
    } catch (e) {
        console.error("Failed to mark notification read:", e);
    }
}

async function markAllNotifsRead() {
    if (!currentUser) return;
    try {
        await fetch(`/api/notifications/user/${currentUser.id}/mark-all-read`, { method: "POST" });
        await loadNotifications();
        logConsole("[Notifications] All notifications marked as read.", "text-muted");
    } catch (e) {
        console.error("Failed to mark all read:", e);
    }
}

/* ═══════════════════════════════════════════════════════════════════
   BOOKING LIFECYCLE ACTIONS (Sprint 6: FR-12) — client for the
   BookingController state machine endpoints.
   ═══════════════════════════════════════════════════════════════════ */
function bookingStatusBadge(status) {
    const s = status || "PENDING";
    return `<span class="status-badge status-${s}">${s}</span>`;
}

let bookingActionInFlight = false;
async function bookingTransition(rideId, action) {
    if (bookingActionInFlight) return;
    bookingActionInFlight = true;
    try {
        const res = await fetch(`/api/bookings/${rideId}/${action}?actorId=${currentUser ? currentUser.id : ""}`, { method: "POST" });
        const data = await res.json();
        if (res.ok) {
            showToast(`Booking #${rideId} ${data.status.toLowerCase()}.`, "success");
            logConsole(`[Booking] Request #${rideId} → ${data.status}`, "text-success");
            await loadAllData();
            await loadNotifications();
        } else {
            showToast(data.error || `Could not ${action} booking.`);
        }
    } catch (e) {
        console.error(`Booking ${action} failed:`, e);
    } finally {
        bookingActionInFlight = false;
    }
}

async function completeTripLifecycle(tripId) {
    if (!(await showConfirm("Mark this trip as completed? All confirmed passengers will be notified to rate you.", "Complete Trip"))) return;
    try {
        const res = await fetch(`/api/bookings/trip/${tripId}/complete?actorId=${currentUser ? currentUser.id : ""}`, { method: "POST" });
        const data = await res.json();
        if (res.ok) {
            logConsole(`[Booking] Trip #${tripId} completed — ${data.completedBookings} booking(s) closed.`, "text-success");
            showToast(`Trip completed! ${data.completedBookings} passenger booking(s) closed.`);
            await loadAllData();
            await loadNotifications();
        } else {
            showToast(data.error || "Could not complete trip.");
        }
    } catch (e) {
        console.error("Complete trip failed:", e);
    }
}

/* ═══════════════════════════════════════════════════════════════════
   UX PRIMITIVES (Sprint 7: UX hardening) — toast notifications,
   styled confirm dialog, and button loading states. Replaces all
   native browser alert/confirm dialogs.
   ═══════════════════════════════════════════════════════════════════ */
function showToast(message, type) {
    if (!type) {
        const m = String(message).toLowerCase();
        if (/(error|failed|could not|cannot|invalid|denied|unable)/.test(m)) type = "error";
        else if (/(success|completed|confirmed|welcome|added|saved|updated)/.test(m)) type = "success";
        else type = "info";
    }
    let container = document.getElementById("toast-container");
    if (!container) {
        container = document.createElement("div");
        container.id = "toast-container";
        document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    const dismiss = () => {
        toast.classList.add("toast-out");
        setTimeout(() => toast.remove(), 260);
    };
    toast.addEventListener("click", dismiss);
    container.appendChild(toast);
    setTimeout(dismiss, 4200);
}

function showConfirm(message, confirmLabel = "Confirm", cancelLabel = "Cancel") {
    return new Promise(resolve => {
        const overlay = document.createElement("div");
        overlay.className = "confirm-overlay";
        overlay.innerHTML = `
            <div class="confirm-box">
                <p></p>
                <div class="confirm-actions">
                    <button class="btn btn-secondary btn-sm confirm-cancel">${cancelLabel}</button>
                    <button class="btn btn-accent btn-sm confirm-ok">${confirmLabel}</button>
                </div>
            </div>`;
        overlay.querySelector("p").textContent = message;
        const close = (result) => { overlay.remove(); resolve(result); };
        overlay.querySelector(".confirm-ok").addEventListener("click", () => close(true));
        overlay.querySelector(".confirm-cancel").addEventListener("click", () => close(false));
        overlay.addEventListener("click", (e) => { if (e.target === overlay) close(false); });
        document.body.appendChild(overlay);
        overlay.querySelector(".confirm-ok").focus();
    });
}

function setBtnLoading(btn, loading) {
    if (!btn) return;
    if (loading) {
        btn.dataset.originalHtml = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<span class="btn-spinner"></span>Working\u2026';
    } else {
        btn.disabled = false;
        if (btn.dataset.originalHtml) btn.innerHTML = btn.dataset.originalHtml;
    }
}

/* ═══════════════════════════════════════════════════════════════════
   MOBILE SIDEBAR (Sprint 7: UX hardening)
   ═══════════════════════════════════════════════════════════════════ */
function toggleSidebar(force) {
    const open = typeof force === "boolean" ? force : !document.body.classList.contains("sidebar-open");
    document.body.classList.toggle("sidebar-open", open);
}

/* Relative time for notification timestamps: "just now", "5m ago", ... */
function relativeTime(iso) {
    try {
        const then = new Date(iso).getTime();
        const diff = Math.max(0, Date.now() - then);
        const min = Math.floor(diff / 60000);
        if (min < 1) return "just now";
        if (min < 60) return `${min}m ago`;
        const hrs = Math.floor(min / 60);
        if (hrs < 24) return `${hrs}h ago`;
        const days = Math.floor(hrs / 24);
        if (days < 7) return `${days}d ago`;
        return formatDateTime(iso);
    } catch (e) {
        return formatDateTime(iso);
    }
}

/* Click-through: open the view that a notification refers to. */
function openNotification(id, type) {
    markNotifRead(id);
    const panel = document.getElementById("notif-panel");
    if (panel) panel.style.display = "none";
    if (!currentUser) return;
    if (type === "BOOKING") {
        navigateTo(currentUser.role === "DRIVER" ? "view-my-trips" : "view-my-bookings");
    } else if (type === "RATING") {
        navigateTo("view-profile");
    }
}

/* ═══════════════════════════════════════════════════════════════════
   DRIVER POLICY ENGINE UI (Sprint 9: FR-15 / FR-16)
   Travel rules (who may ride) + pricing rules (what the ride costs) —
   composed by the driver, interpreted by the backend policy engine.
   ═══════════════════════════════════════════════════════════════════ */
const TRAVEL_RULE_LABELS = {
    MIN_PASSENGER_REPUTATION: v => `Minimum passenger reputation ≥ ${v}`,
    SAME_DESTINATION_ONLY: () => "Passengers must share my destination",
    NO_LARGE_LUGGAGE: () => "No large luggage"
};
const PRICING_RULE_LABELS = {
    BASE_RATE_PER_KM: v => `Base rate ${v} €/km`,
    RUSH_HOUR_SURCHARGE_PCT: v => `Rush-hour surcharge +${v}%`,
    LATE_NIGHT_FEE_EUR: v => `Late-night fee +${v} €`,
    SAME_DESTINATION_DISCOUNT_PCT: v => `Same-destination discount −${v}%`,
    LOYALTY_TIER_DISCOUNT_PCT: v => `Loyalty discount −${v}% for GOLD+ riders`
};

let policyFormsWired = false;

async function loadDriverPolicies() {
    if (!currentUser) return;
    wirePolicyForms();
    try {
        const [tRes, pRes] = await Promise.all([
            fetch(`/api/policies/travel/${currentUser.id}`),
            fetch(`/api/policies/pricing/${currentUser.id}`)
        ]);
        renderRuleList("travel-rules-list", tRes.ok ? await tRes.json() : [],
            TRAVEL_RULE_LABELS, "travel", "No travel rules yet — everyone with a feasible route may book.");
        renderRuleList("pricing-rules-list", pRes.ok ? await pRes.json() : [],
            PRICING_RULE_LABELS, "pricing", "No pricing rules yet — platform default pricing applies.");
    } catch (e) {
        console.error("Failed to load driver policies:", e);
    }
}

function renderRuleList(containerId, rules, labels, kind, emptyText) {
    const box = document.getElementById(containerId);
    if (!box) return;
    if (!rules || rules.length === 0) {
        box.innerHTML = `<div class="info-text" style="padding: 10px;">${emptyText}</div>`;
        return;
    }
    box.innerHTML = rules.map(r => {
        const value = kind === "travel" ? r.numericValue : r.value;
        const label = (labels[r.type] || (() => r.type))(value);
        return `
        <div class="rule-row">
            <div class="rule-desc"><span class="rule-chip">${kind === "travel" ? "RULE" : "PRICE"}</span> ${label}</div>
            <button class="btn btn-sm btn-danger" onclick="deletePolicyRule('${kind}', ${r.id})">Remove</button>
        </div>`;
    }).join("");
}

async function deletePolicyRule(kind, ruleId) {
    try {
        const res = await fetch(`/api/policies/${kind}/rule/${ruleId}`, { method: "DELETE" });
        if (res.ok) {
            showToast("Rule removed.", "success");
            await loadDriverPolicies();
        } else {
            const data = await res.json();
            showToast(data.error || "Could not remove rule.");
        }
    } catch (e) {
        console.error("Rule delete failed:", e);
    }
}

function wirePolicyForms() {
    if (policyFormsWired) return;
    const travelForm = document.getElementById("travel-rule-form");
    const pricingForm = document.getElementById("pricing-rule-form");
    if (!travelForm || !pricingForm) return;
    policyFormsWired = true;

    // The numeric threshold is only meaningful for MIN_PASSENGER_REPUTATION
    const typeSel = document.getElementById("travel-rule-type");
    const valInput = document.getElementById("travel-rule-value");
    const syncValVisibility = () => {
        valInput.style.display = typeSel.value === "MIN_PASSENGER_REPUTATION" ? "" : "none";
    };
    typeSel.addEventListener("change", syncValVisibility);
    syncValVisibility();

    travelForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!currentUser) return;
        const payload = { type: typeSel.value, numericValue: valInput.value || "" };
        try {
            const res = await fetch(`/api/policies/travel/${currentUser.id}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (res.ok) {
                showToast("Travel rule added — it now guards all your trips.", "success");
                valInput.value = "";
                await loadDriverPolicies();
                await loadAllData(); // re-rank candidates
            } else {
                showToast(data.error || "Could not add rule.");
            }
        } catch (err) {
            console.error("Travel rule add failed:", err);
        }
    });

    pricingForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!currentUser) return;
        const payload = {
            type: document.getElementById("pricing-rule-type").value,
            value: document.getElementById("pricing-rule-value").value
        };
        try {
            const res = await fetch(`/api/policies/pricing/${currentUser.id}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (res.ok) {
                showToast("Pricing rule added — your fares now follow your own policy.", "success");
                document.getElementById("pricing-rule-value").value = "";
                await loadDriverPolicies();
            } else {
                showToast(data.error || "Could not add rule.");
            }
        } catch (err) {
            console.error("Pricing rule add failed:", err);
        }
    });
}
