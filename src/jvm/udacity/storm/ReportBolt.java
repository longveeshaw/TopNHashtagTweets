package udacity.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.testing.TestWordSpout;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

import java.util.Map;
import java.util.Arrays;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;

import udacity.storm.*;
import udacity.storm.tools.*;
import udacity.storm.tools.Rankings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
/**
 * A bolt that prints the word and count to redis
 */
public class ReportBolt extends BaseRichBolt
{
  // place holder to keep the connection to redis
  transient RedisConnection<String,String> redis;
  private String[] skipWords = {"http://", "https://", "(", "a", "an", "the", "for", "retweet", "RETWEET", "follow", "FOLLOW"};
  Rankings lastSeenRankablelist = null;
  @Override
  public void prepare(
      Map                     map,
      TopologyContext         topologyContext,
      OutputCollector         outputCollector)
  {
    // instantiate a redis connection
    RedisClient client = new RedisClient("localhost",6379);

    // initiate the actual connection
    redis = client.connect();
  }

  @Override
  public void execute(Tuple tuple)
  {

    String componentId = tuple.getSourceComponent();
    if(componentId.equals("tweet-spout") && lastSeenRankablelist != null) {
      String tweet = tuple.getString(0);
      // provide the delimiters for splitting the tweet
      String delims = "[ .,?!]+";
      // now split the tweet into tokens
      String[] tokens = tweet.split(delims);

      // for each token/word, emit it
      for (String token : tokens) {
        if (token.length() > 3 &&  !Arrays.asList(skipWords).contains(token)) {
          if (token.startsWith("#")) {
            for (Rankable r : lastSeenRankablelist.getRankings()) {
              String word = r.getObject().toString();
              Long count = r.getCount();
              //redis.publish("WordCountTopology", word + "*****|" + Long.toString(30L));
              if (token.equals(word)){
                  redis.publish("WordCountTopology", tweet + "|" + Long.toString(count));
              }
            }
          }
        }
      }
    } else if  (componentId.equals("total-ranker")) {
      // just update the last seen top N hashtags
      lastSeenRankablelist = (Rankings) tuple.getValue(0);
    }
  }

  public void declareOutputFields(OutputFieldsDeclarer declarer)
  {
    // nothing to add - since it is the final bolt
  }
}
