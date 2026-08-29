package com.ankit.joblens.intelligence;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class JobScoreCalculator {
 private final JdbcTemplate jdbc; private final CandidateProfileService profiles;
 public JobScoreCalculator(JdbcTemplate jdbc,CandidateProfileService profiles){this.jdbc=jdbc;this.profiles=profiles;}
 public JobScore calculate(NormalizedJobView j){
  var c=profiles.loadDefault(); var reasons=new ArrayList<JobScore.Reason>();
  var names=new LinkedHashSet<String>(); jdbc.query("SELECT s.canonical_name FROM job_skill js JOIN skill s ON s.id=js.skill_id WHERE js.normalized_job_id=?",(org.springframework.jdbc.core.RowCallbackHandler) rs->names.add(rs.getString(1).toLowerCase(Locale.ROOT)),j.id());
  var matched=c.skills().values().stream().filter(x->names.contains(x.name().toLowerCase(Locale.ROOT))).toList();
  int technical=(int)Math.round(Math.min(weight(c,"weight.technical",40),weight(c,"weight.technical",40)*(matched.stream().mapToDouble(x->x.importance()).sum()/Math.max(1,c.skills().size()))*2));
  if(!matched.isEmpty()) reasons.add(new JobScore.Reason("TECHNICAL",technical,"Matched skills: "+matched.stream().map(CandidateProfileConfig.CandidateSkill::name).sorted().toList()));
  String text=((j.title()==null?"":j.title())+" "+(j.descriptionText()==null?"":j.descriptionText())).toLowerCase(Locale.ROOT);
  long domains=c.domains().stream().filter(text::contains).count(); int domain=(int)Math.min(weight(c,"weight.domain",15),domains*3); if(domain>0) reasons.add(new JobScore.Reason("DOMAIN",domain,"Domain signals matched: "+domains));
  int seniority=text.matches(".*\\b(senior|lead|principal|staff)\\b.*")?weight(c,"weight.seniority",10):0; if(seniority>0) reasons.add(new JobScore.Reason("SENIORITY",seniority,"Senior-level signal found"));
  int location=locationScore(j,c); if(location>0) reasons.add(new JobScore.Reason("LOCATION",location,"Singapore/work arrangement preference matched"));
  int employment="PERMANENT".equals(j.employmentType())?weight(c,"weight.employment",10):("CONTRACT".equals(j.employmentType())?weight(c,"weight.employment",10)/2:0); reasons.add(new JobScore.Reason("EMPLOYMENT",employment,j.employmentType()==null?"Employment type unavailable":j.employmentType()+" preference"));
  int salary=j.salaryMin()!=null||j.salaryMax()!=null?weight(c,"weight.salary",10)/2:Integer.parseInt(c.preferences().getOrDefault("salary.missing.points","3")); reasons.add(new JobScore.Reason("SALARY",salary,j.salaryMin()==null&&j.salaryMax()==null?"Salary unavailable": "Salary provided"));
  int freshness=freshness(j.postedAt(),c); reasons.add(new JobScore.Reason("FRESHNESS",freshness,j.postedAt()==null?"Posted date unavailable":"Fresh posting"));
  int total=Math.max(0,Math.min(100,technical+domain+seniority+location+employment+salary+freshness)); return new JobScore(j.id(),c.id(),total,technical,domain,seniority,location,employment,salary,freshness,reasons);
 }
 private int locationScore(NormalizedJobView j,CandidateProfileConfig c){int max=weight(c,"weight.location",10); int x=j.location()!=null&&j.location().toLowerCase(Locale.ROOT).contains(c.location().toLowerCase(Locale.ROOT))?max/2:0; if("REMOTE".equals(j.remoteType()))x+=max/2; else if("HYBRID".equals(j.remoteType()))x+=max/3; return Math.min(max,x);}
 private int freshness(OffsetDateTime d,CandidateProfileConfig c){if(d==null)return 0; long days=Math.max(0,ChronoUnit.DAYS.between(d,OffsetDateTime.now())); return days<=Integer.parseInt(c.preferences().getOrDefault("freshness.days.full","7"))?weight(c,"weight.freshness",5):days<=Integer.parseInt(c.preferences().getOrDefault("freshness.days.half","30"))?weight(c,"weight.freshness",5)/2:0;}
 private int weight(CandidateProfileConfig c,String key,int d){return Integer.parseInt(c.preferences().getOrDefault(key,Integer.toString(d)));}
}
