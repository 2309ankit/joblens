-- The global skill catalog (V5, V18) was seeded around a Java/backend-engineer profile and a
-- handful of generic business terms. Its résumé skill extractor is an exact-phrase matcher
-- (PhraseAutomaton) against these literal catalog rows, so a sales/account-management résumé
-- only ever matched 'CRM' and 'Oracle' — every sales tool, methodology, and process term below
-- was present in the résumé text but simply had no catalog row to match against.
INSERT INTO skill (canonical_name, category) VALUES
 ('Salesforce', 'SALES'), ('Zoho CRM', 'SALES'), ('Microsoft Dynamics 365', 'SALES'),
 ('HubSpot', 'SALES'), ('ZoomInfo', 'SALES'), ('LinkedIn Sales Navigator', 'SALES'),
 ('Outbound Prospecting', 'SALES'), ('Cold Calling', 'SALES'), ('Cold Email Outreach', 'SALES'),
 ('Lead Generation', 'SALES'), ('Lead Qualification', 'SALES'), ('BANT', 'SALES'),
 ('MEDDIC', 'SALES'), ('Consultative Selling', 'SALES'), ('Solution Selling', 'SALES'),
 ('Account Mining', 'SALES'), ('Account Farming', 'SALES'), ('Account Management', 'SALES'),
 ('Upselling', 'SALES'), ('Cross-selling', 'SALES'), ('Customer Retention', 'SALES'),
 ('Pipeline Management', 'SALES'), ('Sales Forecasting', 'SALES'),
 ('Territory Management', 'SALES'), ('Enterprise Sales', 'SALES'), ('Inside Sales', 'SALES'),
 ('Sales Enablement', 'SALES'), ('Quota Attainment', 'SALES'),
 ('Negotiation', 'BUSINESS'), ('Proposal Management', 'BUSINESS'),
 ('Executive Presentations', 'BUSINESS'), ('Microsoft Office Suite', 'BUSINESS'),
 ('Client Onboarding', 'CUSTOMER_SUCCESS'), ('Product Adoption', 'CUSTOMER_SUCCESS')
ON CONFLICT DO NOTHING;

INSERT INTO skill_alias (alias_name, skill_id)
SELECT alias_name, skill.id
FROM (VALUES
 ('SFDC', 'Salesforce'), ('Salesforce CRM', 'Salesforce'), ('Zoho', 'Zoho CRM'),
 ('Dynamics 365', 'Microsoft Dynamics 365'), ('MS Dynamics', 'Microsoft Dynamics 365'),
 ('HubSpot CRM', 'HubSpot'), ('LinkedIn Navigator', 'LinkedIn Sales Navigator'),
 ('Outbound Sales', 'Outbound Prospecting'), ('Cold Email', 'Cold Email Outreach'),
 ('Cold Outreach', 'Cold Email Outreach'), ('Email Sequencing', 'Cold Email Outreach'),
 ('Lead Gen', 'Lead Generation'), ('MEDDPICC', 'MEDDIC'), ('MEDDICC', 'MEDDIC'),
 ('Key Account Management', 'Account Management'), ('Up-selling', 'Upselling'),
 ('Cross Selling', 'Cross-selling'), ('Retention Strategy', 'Customer Retention'),
 ('Sales Pipeline Management', 'Pipeline Management'), ('RFX', 'Proposal Management'),
 ('RFP', 'Proposal Management'), ('RFP Response', 'Proposal Management'),
 ('Proposal Writing', 'Proposal Management'), ('C-Suite Presentations', 'Executive Presentations'),
 ('MS Office', 'Microsoft Office Suite'), ('MS Office Suite', 'Microsoft Office Suite'),
 ('Microsoft Office', 'Microsoft Office Suite')
) value(alias_name, canonical_name)
JOIN skill ON skill.canonical_name = value.canonical_name
ON CONFLICT (skill_id, lower(alias_name)) DO NOTHING;
