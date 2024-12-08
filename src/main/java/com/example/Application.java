package com.example;

import io.micronaut.runtime.Micronaut;

public class Application {

  // call the ships data every 1 minute
  // stream the responses to a queue
  // inside the queue consumer:
  // find from the DB based on the ship's mmsi ID
  // if found, use geospatial lib to check if the ship was outside or inside polygon
  // compare the current data, determine if an entry/exit event occurred
  // logic:
  // (old: inside, new: inside) && (old: outside, new: outside) don't process
  // (old: inside, new: outside) -> exit event
  // (old: outside, new: inside) -> entry event
  // if an event occurred, store it inside an event collection or separate them by collections
  // finally, regardless of whether event occurred or not, store the most current data into DB
  // store only the most updated data
  // also, flag the ship if inside or outside polygon for easier time filtering for the second endpoint
  public static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }
}
