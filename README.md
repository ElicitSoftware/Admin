<a id="readme-top"></a>

<!-- PROJECT SHIELDS -->
<!--
*** I'm using markdown "reference style" links for readability.
*** Reference links are enclosed in brackets [ ] instead of parentheses ( ).
*** See the bottom of this document for the declaration of the reference variables
*** for contributors-url, forks-url, etc. This is an optional, concise syntax you may use.
*** https://www.markdownguide.org/basic-syntax/#reference-style-links
-->
<!-- [![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![project_license][license-shield]][https://polyformproject.org/licenses/noncommercial/1.0.0]
[![LinkedIn][linkedin-shield]][linkedin-url] -->

<!-- Powered by Michigan -->
<br />
<div align="center">
  <a href="https://innovationpartnerships.umich.edu/" target="_blank">
    <img src="images/stacked.png" alt="Logo" width="15%" >
  </a>
<h3 align="center">Elicit Admin</h3>
  <p>Copyright © 2025 The Regents of the University of Michigan</p>
</div>
<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#built-with">Built With</a>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <!-- <li><a href="#roadmap">Roadmap</a></li> -->
    <li><a href="#configuration">Configuration</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>

<!-- ABOUT THE PROJECT -->
## About The Project
  <p align="left">
    The Admin tool was designed and developed by <a href="https://www.michiganmedicine.org/" target="_blank">Michigan Medicine</a> for the administration of the <a href="https://github.com/elicitsoftware/Elicit" target="_blank">Elicit Software System</a>. It will allow you to upload subjects, monitor their progress, send reminder emails, and view the results. 
    </p>

  <p>
    The authentication and authorization for Admin is controlled by an Open ID Connect (OIDC) server. For testing, we use Keycloak. However, any OIDC compliant software can be used. There are three pre-defined roles:
    <ul>
    <li>elicit_user</li>
    <li>elicit_admin</li>
    <li>elicit_token *</li>
     * Designed to be used by automation systems for adding subjects.
    </ul>
    <table>
      <thead>
        <tr>
          <th>Permission</th>
          <th>elicit_user</th>
          <th>elicit_admin</th>
          <th>elicit_token</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>Search for Subjects</td>
          <td>✅</td>
          <td>✅</td>
          <td>❌</td>
        </tr>
        <tr>
          <td>Register Subjects</td>
          <td>✅</td>
          <td>✅</td>
          <td>❌</td>
        </tr>
        <tr>
          <td>Upload Subjects CSV file</td>
          <td>✅</td>
          <td>✅</td>
          <td>❌</td>
        </tr>        
        <tr>
          <td>Print Subject's report</td>
          <td>✅</td>
          <td>✅</td>
          <td>❌</td>
        </tr>
        <tr>
          <td>Add and edit departments</td>
          <td>❌</td>
          <td>✅</td>
          <td>❌</td>
        </tr>
        <tr>
          <td>Add and edit message templates</td>
          <td>❌</td>
          <td>✅</td>
          <td>❌</td>
        </tr>        
        <tr>
          <td>Add users and manage department associations *</td>
          <td>❌</td>
          <td>✅</td>
          <td>❌</td>
        </tr>                
        <tr>
          <td>Add subjects through the restful web service</td>
          <td>❌</td>
          <td>❌</td>
          <td>✅</td>
        </tr>
      </tbody>
    </table>
    * Authentication and role authorization is managed by the OIDC server. Active/deactivate status and association with departments are managed in the Admin tool. 
  </p>
<!-- [![Product Name Screen Shot][product-screenshot]](https://example.com)

Here's a blank template to get started. To avoid retyping too much info, do a search and replace with your text editor for the following: `github_username`, `repo_name`, `twitter_handle`, `linkedin_username`, `email_client`, `email`, `project_title`, `project_description`, `project_license`
 -->

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Built With

[![Java][Java]][Java-url]
[![Quarkus][Quarkus.io]][Quarkus-url]
[![Vaadin][Vaadin.com]][Vaadin-url]
[![PostgreSQL][Postgresql.com]][Postgresql-url]
[![Maven][Maven.org]][Maven-url]
[![Docker][Docker.com]][Docker-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
## Getting Started
Admin runs in <a href="https://github.com/ElicitSoftware/" target="_blank">Elicit Software</a>, a modular survey system for building and running complex surveys.
This project was built with <a href="http://docker.com" target="_blank">Docker</a> and can be run locally using <a href="https://docs.docker.com/compose/" target="_blank">Docker Compose</a>. After installing Docker, download the `docker-compose.yml` file and follow the instructions provided in that file.

<!-- USAGE EXAMPLES -->
## Usage
The Admin tool doesn't define a survey. The Author tool will be used for that. If you would like to see the Admin tool in practice, you can use the <a href="https://github.com/ElicitSoftware/FHHS" target="_blank">FHHS</a> survey.<br/> 
After running the `docker-compose` command, open [http://localhost:8080](http://localhost:8080) in your browser. Enter any token (the demo accepts any value), complete the questionnaire, and review your data. Once you finalize the survey, a report similar to the one below will be generated.
<div align="center"><image src="images/samplePedigree.png" height=600></div>
<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Configuration
The OIDC server can be congifured with enviromental properties. 
Please visit <a href="https://quarkus.io/guides/security-oidc-configuration-properties-reference" target="_blank">Quarkus.io</a> for a list of configuration properties. 

For email configuration including examples for GMail, AWS SES, Mailjet & SendMail visit <a href="https://quarkus.io/guides/mailer-reference" target="_blank">Quarkus.io</a>
<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ROADMAP
## Roadmap

- [ ] Feature 1
- [ ] Feature 2
- [ ] Feature 3
    - [ ] Nested Feature
 -->

See the [open issues](https://github.com/ElicitSoftware/admin/issues) for a full list of proposed features (and known issues).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTRIBUTING -->
## Contributing

Contributions make the open source community an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

If you have a suggestion to improve this project, please fork the repository and create a pull request. You can also open an issue with the tag "enhancement."
Don't forget to give the project a star! Thank you!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a pull request

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ### Top contributors:

<a href="https://github.com/ElicitSoftware/admin/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=ElicitSoftware/admin" alt="contrib.rocks image" />
</a> -->

<!-- LICENSE -->
## License

Distributed under the PolyForm Noncommercial License 1.0.0. See `LICENSE.txt` for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## News and Journal Articles
Elicit Software supported the MiGHT Study. You can read more about the study [here](https://info.mightstudy.org/news/index.html).

<!-- CONTACT -->
## Contact

Matthew Demerath - m.demerath@elicitsoftware.com<br/>
Project link: [https://github.com/ElicitSoftware/admin](https://github.com/ElicitSoftware/admin)

<p align="right">(<a href="#readme-top">back to top</a>)</p>


<!-- ACKNOWLEDGMENTS -->
## Acknowledgments

<!-- <a href="https://www.michiganmedicine.org"><img src="images/Rogel-Cancer_Logo-Horizontal-CMYK.png" height="30"></a><br/> -->
<br/>
<a href="https://info.mightstudy.org" target="_blank"><img src="images/MiGHT-shortlogo.png" height="50"></a><br/>
<br/>

<p align="right">(<a href="#readme-top">back to top</a>)</p>



<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/ElicitSoftware/admin.svg?style=for-the-badge
[contributors-url]: https://github.com/ElicitSoftware/admin/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/ElicitSoftware/admin.svg?style=for-the-badge
[forks-url]: https://github.com/ElicitSoftware/admin/network/members
[stars-shield]: https://img.shields.io/github/stars/ElicitSoftware/admin.svg?style=for-the-badge
[stars-url]: https://github.com/ElicitSoftware/admin/stargazers
[issues-shield]: https://img.shields.io/github/issues/ElicitSoftware/admin.svg?style=for-the-badge
[issues-url]: https://github.com/ElicitSoftware/admin/issues
[license-shield]: https://img.shields.io/github/license/ElicitSoftware/admin.svg?style=for-the-badge
[license-url]: https://github.com/ElicitSoftware/admin/blob/master/LICENSE.txt
[linkedin-shield]: https://img.shields.io/badge/-LinkedIn-black.svg?style=for-the-badge&logo=linkedin&colorB=555
[linkedin-url]: https://linkedin.com/in/linkedin_username
[product-screenshot]: images/screenshot.png
[Quarkus.io]: https://img.shields.io/badge/quarkus-000000?style=for-the-badge&logo=quarkus&logoColor=white
[Quarkus-url]: https://quarkus.io/
[Vaadin.com]: https://img.shields.io/badge/Vaadin-20232A?style=for-the-badge&logo=vaadin&logoColor=61DAFB
[Vaadin-url]: https://vaadin.com/
[Postgresql.com]: https://img.shields.io/badge/postgresql-white?style=for-the-badge&logo=postgresql&logoColor=blue
[Postgresql-url]: https://postgresql.org/
[Docker.com]: https://img.shields.io/badge/docker-257bd6?style=for-the-badge&logo=docker&logoColor=white
[Docker-url]: https://docker.com
[Java]: https://img.shields.io/badge/Java-3a75b0?style=for-the-badge&logo=openjdk&logoColor=white
[Java-url]: https://dev.java/
[Maven.org]:https://img.shields.io/badge/Maven-000000?style=for-the-badge&logo=apachemaven&logoColor=blue
[Maven-url]: https://maven.apache.org/
