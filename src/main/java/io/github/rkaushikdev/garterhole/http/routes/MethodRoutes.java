package io.github.rkaushikdev.garterhole.http.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Pagination;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.UriComponents;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Problem;
import io.github.rkaushikdev.garterhole.validation.Schema;
import io.github.rkaushikdev.garterhole.validation.Schemas;
import io.github.rkaushikdev.garterhole.validation.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The method library over HTTP.
 *
 * <p>The interesting one is the analyse route, which takes a calling and gives
 * back what it actually comes to. A caller sends the calling they mean to ring
 * and finds out before the band turns up whether it is true, how long it is and
 * how it is built.
 *
 * <p>Nothing here trusts the class or the lead head a caller sends. Those are
 * worked out from the notation on the way in, which is why the request body has
 * no field for either of them.
 */
public final class MethodRoutes {

    /** What a new method has to say about itself. */
    private static final Schema NEW_METHOD = Schemas.object(Fields.of(
            Map.entry("name", Schemas.string().min(1).max(80)),
            Map.entry("stage", Types.stage()),
            Map.entry("notation", Schemas.string().min(1).max(400))));

    /** What an analysis request has to say. */
    private static final Schema ANALYSIS = Schemas.object(Fields.of(Map.entry("touch", Types.touch())));

    private MethodRoutes() {}

    /**
     * Add the routes to a router.
     *
     * @param router the router
     * @return the router
     */
    public static Router add(Router router) {
        router.get("/methods", (request, context) -> {
            Optional<String> wantedStage = request.queryValue("stage");
            Optional<String> wantedClass = request.queryValue("class");
            List<JsonNode> filtered = context.service().listMethods().stream()
                    .filter(entry -> wantedStage.isEmpty()
                            || Json.templateText(entry.path("stage")).equals(wantedStage.get()))
                    .filter(entry -> wantedClass.isEmpty()
                            || wantedClass.get().equals(entry.path("class").textValue()))
                    .toList();
            return Response.json(Pagination.paginate(request, filtered, "methods"));
        });

        router.post("/methods", (request, context) -> {
            JsonNode body = Schemas.check(NEW_METHOD, request.body(), "the method");
            checkNotationAgainstStage(body);
            ObjectNode document = context.service()
                    .addMethod(new Method.Definition(
                            body.path("name").textValue(),
                            body.path("stage").intValue(),
                            body.path("notation").textValue()));
            return Response.created(
                    "/methods/" + UriComponents.encode(document.path("title").textValue()), document);
        });

        router.get("/methods/:title", (request, context) -> {
            Schemas.check(Types.methodTitle(), TextNode.valueOf(request.param("title")), "the title");
            return Response.json(context.service().describeMethod(request.param("title")));
        });

        router.delete("/methods/:title", (request, context) -> {
            boolean removed = context.service().removeMethod(request.param("title"));
            return removed ? Response.noContent() : Response.json(Json.object().put("removed", false), 404);
        });

        router.post("/methods/:title/analyse", (request, context) -> {
            JsonNode body = Schemas.check(ANALYSIS, request.body(), "the analysis request");
            return Response.json(context.service()
                    .analyse(request.param("title"), body.path("touch").textValue()));
        });

        return router;
    }

    /**
     * The notation has to be readable at the stage the caller named, and the
     * schema cannot check one field against another, so it is done here.
     */
    private static void checkNotationAgainstStage(JsonNode body) {
        List<Problem> problems =
                Types.placeNotation(body.path("stage").intValue()).problems(body.path("notation"), "notation");
        if (!problems.isEmpty()) {
            throw ValidationException.withIssues(
                    "the method is wrong: notation " + problems.get(0).message(), problems);
        }
    }
}
