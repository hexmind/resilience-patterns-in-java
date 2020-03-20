package pl.ts;

import feign.Param;
import feign.RequestLine;

import java.util.List;

interface BoatsApi {

    @RequestLine("GET /api/boats")
    List<Boat> getBoats();

    @RequestLine("GET /api/boats/{id}")
    Boat getBoat(@Param("id") Long id);

}
