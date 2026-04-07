import pytest
from datetime import datetime
from src.core.models import User, Task, Project


class TestUser:
    def test_user_creation(self):
        created_at = datetime.now()
        user = User(
            id=1,
            username="testuser",
            email="test@example.com",
            created_at=created_at
        )
        
        assert user.id == 1
        assert user.username == "testuser"
        assert user.email == "test@example.com"
        assert user.created_at == created_at
        assert user.is_active is True

    def test_user_inactive(self):
        user = User(
            id=1,
            username="testuser",
            email="test@example.com",
            created_at=datetime.now(),
            is_active=False
        )
        
        assert user.is_active is False


class TestTask:
    def test_task_creation(self):
        created_at = datetime.now()
        task = Task(
            id=1,
            title="Test Task",
            description="A test task",
            user_id=1,
            created_at=created_at
        )
        
        assert task.id == 1
        assert task.title == "Test Task"
        assert task.description == "A test task"
        assert task.user_id == 1
        assert task.created_at == created_at
        assert task.completed is False
        assert task.due_date is None

    def test_task_with_due_date(self):
        created_at = datetime.now()
        due_date = datetime(2024, 12, 31)
        
        task = Task(
            id=1,
            title="Test Task",
            description="A test task",
            user_id=1,
            created_at=created_at,
            due_date=due_date
        )
        
        assert task.due_date == due_date


class TestProject:
    def test_project_creation(self):
        created_at = datetime.now()
        project = Project(
            id=1,
            name="Test Project",
            description="A test project",
            owner_id=1,
            created_at=created_at
        )
        
        assert project.id == 1
        assert project.name == "Test Project"
        assert project.description == "A test project"
        assert project.owner_id == 1
        assert project.created_at == created_at
        assert project.is_active is True
        assert project.tasks == []

    def test_project_with_tasks(self):
        created_at = datetime.now()
        task = Task(
            id=1,
            title="Task 1",
            description="First task",
            user_id=1,
            created_at=created_at
        )
        
        project = Project(
            id=1,
            name="Test Project",
            description="A test project",
            owner_id=1,
            created_at=created_at,
            tasks=[task]
        )
        
        assert len(project.tasks) == 1
        assert project.tasks[0] == task
