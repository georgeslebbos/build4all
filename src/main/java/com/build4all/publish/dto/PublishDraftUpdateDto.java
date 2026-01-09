package com.build4all.publish.dto;


import java.util.List;

public class PublishDraftUpdateDto {

    // Step 1
    private String applicationName;
    private String shortDescription;
    private String fullDescription;

    // Step 2
    private String category;
    private String countryAvailability;
    private String pricing; // FREE / PAID
    private Boolean contentRatingConfirmed;

    // Step 4
    private String appIconUrl;
    private List<String> screenshotsUrls;

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getFullDescription() { return fullDescription; }
    public void setFullDescription(String fullDescription) { this.fullDescription = fullDescription; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCountryAvailability() { return countryAvailability; }
    public void setCountryAvailability(String countryAvailability) { this.countryAvailability = countryAvailability; }

    public String getPricing() { return pricing; }
    public void setPricing(String pricing) { this.pricing = pricing; }

    public Boolean getContentRatingConfirmed() { return contentRatingConfirmed; }
    public void setContentRatingConfirmed(Boolean contentRatingConfirmed) { this.contentRatingConfirmed = contentRatingConfirmed; }

    public String getAppIconUrl() { return appIconUrl; }
    public void setAppIconUrl(String appIconUrl) { this.appIconUrl = appIconUrl; }

    public List<String> getScreenshotsUrls() { return screenshotsUrls; }
    public void setScreenshotsUrls(List<String> screenshotsUrls) { this.screenshotsUrls = screenshotsUrls; }
}

