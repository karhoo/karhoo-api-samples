package com.karhoo.demo;

import com.karhoo.demo.model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

@SpringBootApplication
public class KarhooDemoApplication {

    public static String BOOKING_URL = "/v1/bookings";
    public static String BOOKING_STATUS_URL = "/v1/bookings/%s/status";
    public static String BOOKING_HISTORY_URL = "/v1/bookings/%s/history";
    public static String FINAL_FARE_URL = "/v1/fares/trip/%s";
    public static String FOLLOW_BOOKING_URL = "/v1/bookings/follow/%s";
    public static String QUOTES_REQUEST_URL = "/v1/quotes";
    public static String QUOTES_LIST_URL = "/v1/quotes/%s";
    public static String TRACK_DRIVER_URL = "/v1/bookings/%s/track";
    public static int MAX_WAIT_TIME = 3000;

    public static void main(String[] args) throws Exception {

        SpringApplication.run(KarhooDemoApplication.class, args);
        /**
         * Step 1 - Choosing the correct environment and configuration setup.
         * In this sample the host and scheme are set in KarhooApiClient
         * Karhoo provides a Sandbox environment that you should use for testing the integration and a
         * Production environment that can be used when you go live.
         * The Sandbox environment is available at `https://rest.sandbox.karhoo.com`
         * The Production environment is available at `https://rest.karhoo.com`
         * Karhoo will provide you with these credentials.
         * Credentials for Production should be stored securely on your server
         *
         * Step 2 - Authenticate and retrieve a JWT and refresh token
         **/
        String username = "[USERNAME_PLACEHOLDER]";
        String password = "[PASSWORD_PLACEHOLDER]";
        // if you are using a Serverless/Lambda architecture please provide in here the cached JWT and Refresh Token
        KarhooApiClient karhooClient = new KarhooApiClient(username, password, "", "");

        // Only make the call below if you you don't have a cached JWT or Refresh Token
        // Refresh tokens are valid for 30 days and the jwt token has a validity of 1 hour.
        karhooClient.authenticate();
        // You should now cache the JWT and refresh token in Redis is using a Serverless architecture or a large number of replicas

        /**
         * Step 3 - Get the place id for origin and destination
         * For the quote request at Step 4 we will need the place_id for origin and destination.
         * These PlaceIDs can be obtained in multiple ways:
         * - If you use Google Places you can just use the Google Place ID
         * - For airports and train stations Karhoo can provide you with a list of ids for these POIs (Points of interest)
         * - You can use the Reverse Geocode endpoint https://developer.karhoo.com/reference#get_reverse-geocode
         * - You can use our autocomplete functionality https://developer.karhoo.com/reference#post_address-autocomplete
         * - We are working on an endpoint that allows you to pass in the Address details (business_name, street, number, postcode, latitude and longitude)
         **/

        // Here are two example Google Place IDs we will use for this example. You can see the address associated to them using our endpoint https://developer.karhoo.com/reference#post_place-details
        String originPlaceId = "ChIJtV5bzSAFdkgRpwLZFPWrJgo";
        String destinationPlaceId = "ChIJO14pRXYbdkgRkM-CgzxxADY";

        /**
         * Step 4 - Make the quotes request
         **/
        QuoteList quoteList = getQuotes(karhooClient, originPlaceId, destinationPlaceId);

        /**
         * Step 5 - Book the trip
         **/
        BookingResponse bookingResponse = bookTrip(karhooClient, quoteList.quote_items.get(0).quote_id);

        /**
         * Step 6 - Poll for trip updates after making a booking
         **/
        getBookingStatus(karhooClient, bookingResponse.id);

        getBookingHistory(karhooClient, bookingResponse.id);

        /**
         * Step 7 - Poll for driver tracking
         **/
        Thread.sleep(10000);
        trackDriver(karhooClient, bookingResponse.id);
//        trackDriver(karhooClient, "a13ce6a8-3125-483a-9206-80849fcec415");

        // Retrieves booking details for the passenger without being logged in
        followBooking(karhooClient, bookingResponse.follow_code);

        /**
         * Step 7 - Poll for final fare
         **/
//        getFinalFare(karhooClient, "a13ce6a8-3125-483a-9206-80849fcec415");
        getFinalFare(karhooClient, bookingResponse.id);

        System.out.println("Demo complete");
    }

    private static BookingResponse bookTrip(KarhooApiClient karhooClient, String quoteId) throws Exception {
        BookingRequest bookingRequest = createBookingRequest(quoteId);
        BookingResponse bookingResponse = karhooClient.POST(BOOKING_URL, bookingRequest, BookingResponse.class);
        System.out.println("Booking id: " + bookingResponse.id);

        return bookingResponse;
    }

    private static BookingRequest createBookingRequest(String quoteId) {
        //Create the list of passengers for the booking
        PassengerDetails passenger1 = new PassengerDetails("Joe", "Bloggs", "test1.test@test.test", "+447777111111", "en_GB");
        PassengerDetails passenger2 = new PassengerDetails("Jane", "Doe", "test2.test@test.test", "+447777111112", "en_GB");
        Passengers passengers = new Passengers();
        passengers.passenger_details = new ArrayList<>();
        passengers.passenger_details.add(passenger1);
        passengers.passenger_details.add(passenger2);

        BookingRequest bookingRequest = new BookingRequest();
        bookingRequest.passengers = passengers;
        bookingRequest.quote_id = quoteId;

        return bookingRequest;
    }

    private static void getFinalFare(KarhooApiClient karhooClient, String tripId) throws Exception {
        long startTime = System.nanoTime();
        while (((System.nanoTime() - startTime) / 1000000 < MAX_WAIT_TIME)) {
            Thread.sleep(1000); // first wait for 2 seconds for updates
            karhooClient.GET(String.format(FINAL_FARE_URL, tripId), Fare.class);
        }
    }

    private static void followBooking(KarhooApiClient karhooClient, String followCode) throws Exception {
        long startTime = System.nanoTime();
        while (((System.nanoTime() - startTime) / 1000000 < MAX_WAIT_TIME)) {
            Thread.sleep(1000); // first wait for 3 seconds for updates
            karhooClient.GET(String.format(FOLLOW_BOOKING_URL, followCode), BookingResponse.class);
        }
    }

    private static void getBookingStatus(KarhooApiClient karhooClient, String tripId) throws Exception {
        long startTime = System.nanoTime();
        while (((System.nanoTime() - startTime) / 1000000 < MAX_WAIT_TIME)) {
            Thread.sleep(1000); // first wait for 2 seconds for updates
            karhooClient.GET(String.format(BOOKING_STATUS_URL, tripId), BookingStatus.class);
        }
    }

    private static void getBookingHistory(KarhooApiClient karhooClient, String tripId) throws Exception {
        karhooClient.GET(String.format(BOOKING_HISTORY_URL, tripId), BookingHistory.class);
    }

    private static QuoteList getQuotes(KarhooApiClient karhooClient, String originPlaceId, String destinationPlaceId) throws Exception {
        QuoteRequest quoteRequestBody = createQuoteRequest(originPlaceId, destinationPlaceId);
        QuoteList quotesList = karhooClient.POST(QUOTES_REQUEST_URL, quoteRequestBody, QuoteList.class);
        String quotesListId = quotesList.id;

        // Now we need to keep polling until all fleets return a quote to the Karhoo marketplace. The latency of the fleets varies,
        // some might respond in 1 second while others might require up to 4 seconds.
        long startTime = System.nanoTime();
        while (((System.nanoTime() - startTime) / 1000000 < MAX_WAIT_TIME) && (!quotesList.status.equals("COMPLETED"))) {
            Thread.sleep(1000); // first wait for 1 second to allow more fleets to respond
            quotesList = karhooClient.GET(String.format(QUOTES_LIST_URL, quotesListId), QuoteList.class);

            pushQuotesToYourUser(quotesList);
        }

        return quotesList;
    }

    private static QuoteRequest createQuoteRequest(String originPlaceId, String destinationPlaceId) {
        QuoteRequest quoteRequestBody = new QuoteRequest();
        quoteRequestBody.origin_place_id = originPlaceId;
        quoteRequestBody.destination_place_id = destinationPlaceId;
        quoteRequestBody.local_time_of_pickup = "2020-08-24T09:15";
        return quoteRequestBody;
    }

    static void pushQuotesToYourUser(QuoteList list) {
        // If your platform allows you to push new quotes to your user as you retrieve them here is where you should do it.
        // This way it is more interactive for your user as they progressively receive more and more quotes
        // and you can still wait until the list is `COMPLETED` which guarantees no other quotes will be returned.
    }

    private static void trackDriver(KarhooApiClient karhooClient, String tripId) throws Exception {
        long startTime = System.nanoTime();
        while (((System.nanoTime() - startTime) / 1000000 < MAX_WAIT_TIME)) {
            Thread.sleep(1000); // first wait for 3 seconds for updates
            karhooClient.GET(String.format(TRACK_DRIVER_URL, tripId), TripInfo.class);
        }
    }
}
