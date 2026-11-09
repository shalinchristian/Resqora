// Built-in skill list for suggestions in the builder. Local only, so it never costs an AI call.
const GROUPS = {
  Languages: 'JavaScript, TypeScript, Python, Java, C, C++, C#, Go, Rust, Kotlin, Swift, PHP, Ruby, Scala, R, Dart, MATLAB, Bash, PowerShell, Perl, Objective-C, Haskell, Elixir, Lua, Solidity, SQL, PL/SQL, T-SQL, HTML, CSS, Sass',
  Frontend: 'React, Next.js, Angular, Vue.js, Nuxt.js, Svelte, Redux, Zustand, React Query, Tailwind CSS, Bootstrap, Material UI, Chakra UI, jQuery, Vite, Webpack, Babel, Storybook, Three.js, D3.js, Framer Motion, React Native, Flutter, Ionic, Electron, Responsive Design, Accessibility (WCAG), Figma',
  Backend: 'Node.js, Express.js, NestJS, Spring Boot, Spring Security, Hibernate, JPA, Django, Flask, FastAPI, ASP.NET Core, .NET, Entity Framework, Laravel, Ruby on Rails, GraphQL, REST APIs, gRPC, WebSockets, Microservices, OAuth 2.0, JWT, Kafka, RabbitMQ, Celery, Socket.IO',
  Databases: 'PostgreSQL, MySQL, MongoDB, Redis, SQLite, Oracle, SQL Server, Cassandra, DynamoDB, Elasticsearch, Firebase, Supabase, Neo4j, MariaDB, Snowflake, BigQuery, Prisma, Mongoose',
  'Cloud & DevOps': 'AWS, AWS EC2, AWS S3, AWS Lambda, AWS RDS, Azure, Google Cloud, Docker, Kubernetes, Terraform, Ansible, Jenkins, GitHub Actions, GitLab CI, CI/CD, Linux, Nginx, Apache, Helm, Prometheus, Grafana, Render, Vercel, Netlify, Heroku, Cloudflare',
  Data: 'Pandas, NumPy, Matplotlib, Seaborn, Excel, Advanced Excel, Power BI, Tableau, Looker, Talend, Apache Spark, PySpark, Hadoop, Airflow, dbt, ETL, Data Analysis, Data Visualization, Data Cleaning, Statistics, A/B Testing, Google Analytics, SPSS, Jupyter',
  'AI & ML': 'Machine Learning, Deep Learning, scikit-learn, TensorFlow, PyTorch, Keras, OpenCV, NLP, Computer Vision, LLMs, LangChain, Hugging Face, Prompt Engineering, RAG, XGBoost, Generative AI, MLOps',
  Testing: 'Jest, Vitest, Mocha, Cypress, Playwright, Selenium, JUnit, Mockito, pytest, Postman, Unit Testing, Integration Testing, TDD, Manual Testing, API Testing, LoadRunner, JMeter',
  Tools: 'Git, GitHub, GitLab, Bitbucket, Jira, Confluence, Notion, Trello, VS Code, IntelliJ IDEA, Eclipse, Android Studio, Xcode, Swagger, Linux Shell, Agile, Scrum, Kanban',
  CS: 'Data Structures, Algorithms, OOP, System Design, Low-Level Design, Design Patterns, Operating Systems, Computer Networks, DBMS, Multithreading, Problem Solving, Competitive Programming',
  Design: 'UI Design, UX Design, Wireframing, Prototyping, Adobe XD, Photoshop, Illustrator, Canva, User Research',
  Business: 'Communication, Leadership, Teamwork, Project Management, Stakeholder Management, Product Management, Digital Marketing, SEO, Content Writing, Public Speaking, Time Management, Customer Service, Sales, Financial Analysis, Tally, SAP',
  Security: 'Cybersecurity, Network Security, Penetration Testing, OWASP, Wireshark, Burp Suite, Kali Linux, Cryptography',
  Mobile: 'Android, iOS, Jetpack Compose, SwiftUI, Expo',
}

export const SKILLS = Object.entries(GROUPS).flatMap(([group, list]) =>
  list.split(',').map((name) => ({ name: name.trim(), group })))

const norm = (s) => s.toLowerCase().replace(/[^a-z0-9+#]/g, '')

// starts-with matches first, then word starts, then anywhere; skips skills already added
export function suggestSkills(query, taken = [], limit = 6) {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const nq = norm(q)
  const have = new Set(taken.map((t) => norm(t)))
  const scored = []
  for (const s of SKILLS) {
    if (have.has(norm(s.name))) continue
    const n = s.name.toLowerCase()
    let score = -1
    if (n.startsWith(q)) score = 0
    else if (n.split(/[\s/.(-]+/).some((w) => w.startsWith(q))) score = 1
    else if (nq.length >= 3 && norm(n).includes(nq)) score = 2
    if (score >= 0) scored.push({ ...s, score })
  }
  const seen = new Set()
  return scored.sort((a, b) => a.score - b.score || a.name.length - b.name.length)
    .filter((s) => (seen.has(s.name) ? false : seen.add(s.name)))
    .slice(0, limit)
}
