import pytest

class TestHTMLStatus:
    def test_html_status_endpoint(self, client):
        response = client.get("/hardware/status/html")
        assert response.status_code == 200
        assert "text/html" in response.headers["content-type"]
        content = response.text
        assert "<!DOCTYPE html>" in content
        assert "ZombiePlant Status" in content
        assert "environment" in content.lower()
        assert "Water System" in content or "water system" in content.lower()
        # Check for Javascript refresh logic instead of meta tag
        assert "REFRESH_INTERVAL = 3000" in content
        assert "window.location.reload()" in content
        
        # Check for the pause button
        assert 'id="toggleBtn"' in content
