-- message_types
INSERT INTO survey.message_types(id, name) VALUES(NEXTVAL('survey.message_types_seq'),'email');
-- Departments
INSERT INTO survey.departments(id, name, code, default_message_id, notification_emails, from_email) VALUES(NEXTVAL('survey.departments_seq'), 'Testing Department','Test', '1', null, 'test@testdepartment.org');
INSERT INTO survey.departments(id, name, code, default_message_id, notification_emails, from_email) VALUES(NEXTVAL('survey.departments_seq'), 'Dev Department','Dev', '1', null, 'test@testdepartment.org');
--message_templates
INSERT INTO survey.message_templates(id, department_id, message_type_id, subject, message, mime_type) VALUES(NEXTVAL('survey.message_templates_seq'), 1, 1, 'Family History Request from Testing Department (Dev)','Hello,<br>You are receiving this notice because you have an upcoming appointment in the Test Department office.<br><br>Our practice, in collaboration with the Michigan Oncology Quality Consortium (MOQC) and the University of Michigan, is testing a new way for patients to easily share their family history with their healthcare team. This no-cost MiGHT Family Cancer History Tool is designed to help you gather information about your family history of cancer.<br>
Using this tool may benefit you and your family because the information you share:<br>
•	Could impact treatment decisions<br>
•	Could impact plans to monitor your health<br>
•	Can help inform family members of their cancer risk<br>
•	Provides a pedigree (drawing) of your family history<br>
<br>
This tool will ask questions about your blood-related family members (parents, siblings, children, grandparents, aunts and uncles) and their:<br>
•	Current age (if alive) or age at death<br>
•	Cancer diagnoses and ages at diagnosis<br>
<br>
Access Instructions for the MiGHT Family Cancer History Tool:<br>
•	Use this personalized link to access the tool:<br>
• http://localhost/#/login/<TOKEN><br>
•	We ask that you complete the tool before your visit, if possible. The amount of time needed varies based on the size of your family, but it takes most people about 15 minutes.<br>
•	If you cannot finish the tool in one sitting, you can stop at any time and your responses will save automatically. Use the link above to go back to your partially completed form.<br>
•	Please fill out as much as you can, even if you don’t know all the details.<br>
<br>
Please note that this tool is under development and results should be reviewed with your medical provider for accuracy. Your results will only be used by your medical care team to assist in providing your care.<br>
If you have any questions, please email MiGHT@moqc.org, or visit info.mightstudy.org/.<br>
We appreciate you taking the time to help us evaluate how we can use this tool to take better care of patients and families. Thank you for trusting us with your clinical care.<br><br>
Test Department','text/html');
-- Alice Admin
INSERT INTO survey.users(id, username, first_name, last_name) VALUES (NEXTVAL('survey.users_seq'), 'admin','Alice', 'Admin');
INSERT INTO survey.user_surveys(user_id, survey_id) VALUES(CURRVAL('survey.users_seq'),1);
INSERT INTO survey.user_departments(department_id, user_id) VALUES(1,CURRVAL('survey.users_seq'));
INSERT INTO survey.user_departments(department_id, user_id) VALUES(2,CURRVAL('survey.users_seq'));
-- Umar User
INSERT INTO survey.users(id, username, first_name, last_name) VALUES (NEXTVAL('survey.users_seq'), 'user','Umar', 'User');
INSERT INTO survey.user_surveys(user_id, survey_id) VALUES(CURRVAL('survey.users_seq'),1);
INSERT INTO survey.user_departments(department_id, user_id) VALUES(1,CURRVAL('survey.users_seq'));
