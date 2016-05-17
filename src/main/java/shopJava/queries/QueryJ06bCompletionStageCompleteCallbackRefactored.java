package shopJava.queries;

import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import org.bson.Document;
import shopJava.model.Order;
import shopJava.model.User;
import shopJava.model.Credentials;
import shopJava.model.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static com.mongodb.client.model.Filters.eq;
import static java.lang.Thread.sleep;
import static shopJava.util.Constants.*;
import static shopJava.util.Util.checkUserLoggedIn;

@SuppressWarnings("Convert2MethodRef")
public class QueryJ06bCompletionStageCompleteCallbackRefactored {

    public static void main(String[] args) throws Exception {
        new QueryJ06bCompletionStageCompleteCallbackRefactored();
    }

    private final DAO dao = new DAO();

    private class DAO {

        private final MongoCollection<Document> usersCollection;
        private final MongoCollection<Document> ordersCollection;

        DAO() {
            final MongoClient client = MongoClients.create();
            final MongoDatabase db = client.getDatabase(SHOP_DB_NAME);
            this.usersCollection = db.getCollection(USERS_COLLECTION_NAME);
            this.ordersCollection = db.getCollection(ORDERS_COLLECTION_NAME);
        }

        private <T> SingleResultCallback<T> callbackToCompleteFuture(final CompletableFuture<T> future) {
            return (result, t) -> {
                if (t == null) {
                    future.complete(result);
                } else {
                    future.completeExceptionally(t);
                }
            };
        }

        CompletionStage<Optional<User>> findUserByName(final String name) {

            final CompletableFuture<Optional<User>> future = new CompletableFuture<>();

            usersCollection
                    .find(eq("_id", name))
                    .map(doc -> (Optional<User>)(doc == null ? Optional.empty() : Optional.of(new User(doc))))
                    .first(callbackToCompleteFuture(future));

            return future;
        }

        CompletionStage<List<Order>> findOrdersByUsername(final String username) {

            final CompletableFuture<List<Order>> future = new CompletableFuture<>();

            ordersCollection
                    .find(eq("username", username))
                    .map(doc -> new Order(doc))
                    .into(new ArrayList<>(), callbackToCompleteFuture(future));

            return future;
        }
    }   // end DAO


    private CompletionStage<String> logIn(final Credentials credentials) {
        return dao.findUserByName(credentials.username)
                .thenApply(optUser -> checkUserLoggedIn(optUser, credentials))
                .thenApply(user -> user.name);

    }

    private CompletionStage<Result> processOrdersOf(final String username) {
        return dao.findOrdersByUsername(username)
                .thenApply(orders -> new Result(username, orders));
    }

    private void eCommercStatistics(final Credentials credentials) throws Exception {

        System.out.println("--- Calculating eCommerce statistings for user \"" + credentials.username + "\" ...");

        final CountDownLatch latch = new CountDownLatch(1);

        logIn(credentials)
                .thenCompose(username -> processOrdersOf(username))     // flatMap of CompletionStage
                .whenComplete((result, t) -> {
                    if (t == null) {
                        result.display();
                    } else {
                        System.err.println(t.toString());
                    }
                    latch.countDown();
                });

        latch.await();
    }

    private QueryJ06bCompletionStageCompleteCallbackRefactored() throws Exception {

        eCommercStatistics(new Credentials(LISA, "password"));
        sleep(2000L);
        eCommercStatistics(new Credentials(LISA, "bad_password"));
        sleep(2000L);
        eCommercStatistics(new Credentials(LISA.toUpperCase(), "password"));
    }
}