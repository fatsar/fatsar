const locateButton = document.getElementById("locate");
const statusText = document.getElementById("status");
const resultsList = document.getElementById("results");
const radiusInput = document.getElementById("radius");
const radiusValue = document.getElementById("radius-value");

const formatDistance = (meters) => {
  if (meters < 1000) {
    return `${Math.round(meters)} m`;
  }
  return `${(meters / 1000).toFixed(2)} km`;
};

const toRadians = (value) => (value * Math.PI) / 180;

const getDistanceMeters = (origin, destination) => {
  const earthRadius = 6371e3;
  const lat1 = toRadians(origin.lat);
  const lat2 = toRadians(destination.lat);
  const deltaLat = toRadians(destination.lat - origin.lat);
  const deltaLon = toRadians(destination.lon - origin.lon);

  const a =
    Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) *
    Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return earthRadius * c;
};

const updateStatus = (message) => {
  statusText.textContent = message;
};

const updateRadiusValue = () => {
  radiusValue.textContent = `${radiusInput.value} m`;
};

const buildOverpassQuery = (lat, lon, radius) => `
  [out:json];
  (
    node["amenity"="toilets"](around:${radius},${lat},${lon});
    way["amenity"="toilets"](around:${radius},${lat},${lon});
    relation["amenity"="toilets"](around:${radius},${lat},${lon});
  );
  out center tags;
`;

const renderResults = (restrooms) => {
  resultsList.innerHTML = "";

  if (restrooms.length === 0) {
    resultsList.innerHTML = "<li class=\"result\">No public restrooms found in this area.</li>";
    return;
  }

  restrooms.forEach((restroom) => {
    const item = document.createElement("li");
    item.className = "result";

    const title = document.createElement("h3");
    title.textContent = restroom.name || "Public restroom";

    const address = document.createElement("p");
    address.textContent = restroom.address || "Address not listed";

    const meta = document.createElement("p");
    meta.className = "result__meta";
    meta.textContent = `${formatDistance(restroom.distance)} • ${restroom.access}`;

    item.append(title, address, meta);
    resultsList.appendChild(item);
  });
};

const fetchRestrooms = async (position) => {
  const { latitude, longitude } = position.coords;
  const radius = Number(radiusInput.value);

  updateStatus("Searching for nearby restrooms...");
  resultsList.innerHTML = "";

  const response = await fetch("https://overpass-api.de/api/interpreter", {
    method: "POST",
    body: buildOverpassQuery(latitude, longitude, radius),
  });

  if (!response.ok) {
    updateStatus("Unable to reach the restroom data source. Please try again.");
    return;
  }

  const data = await response.json();
  const origin = { lat: latitude, lon: longitude };

  const restrooms = data.elements
    .map((element) => {
      const coords = element.type === "node"
        ? { lat: element.lat, lon: element.lon }
        : { lat: element.center?.lat, lon: element.center?.lon };

      if (!coords.lat || !coords.lon) {
        return null;
      }

      const tags = element.tags || {};
      const addressParts = [
        tags["addr:housenumber"],
        tags["addr:street"],
        tags["addr:city"],
      ].filter(Boolean);

      return {
        name: tags.name,
        address: addressParts.length ? addressParts.join(" ") : tags.description,
        access: tags.access === "yes" ? "Public access" : tags.access === "customers" ? "Customers only" : "Access unknown",
        distance: getDistanceMeters(origin, coords),
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.distance - b.distance);

  updateStatus(`Found ${restrooms.length} restroom${restrooms.length === 1 ? "" : "s"} nearby.`);
  renderResults(restrooms);
};

const handleLocationError = (error) => {
  let message = "Location access failed. Please allow location permissions.";
  if (error.code === error.TIMEOUT) {
    message = "Location request timed out. Please try again.";
  }
  updateStatus(message);
};

locateButton.addEventListener("click", () => {
  updateStatus("Requesting your location...");
  navigator.geolocation.getCurrentPosition(fetchRestrooms, handleLocationError, {
    enableHighAccuracy: true,
    timeout: 10000,
  });
});

radiusInput.addEventListener("input", updateRadiusValue);

updateRadiusValue();
