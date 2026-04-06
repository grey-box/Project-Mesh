async function includeHTML(elementId, filePath) {
    let response = await fetch(filePath);
    if (response.status === 200) {
        let content = await response.text();
        document.getElementById(elementId).innerHTML = content;
    } else {
        document.getElementById(elementId).innerHTML = "Page not found.";
    }
}

// footer timestamp
includeHTML("footer-timestamp", "./footer_timestamp/timestamp.txt")